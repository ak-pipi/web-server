import * as cdk from 'aws-cdk-lib';
import { Duration, RemovalPolicy, Tags } from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as acm from 'aws-cdk-lib/aws-certificatemanager';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as origins from 'aws-cdk-lib/aws-cloudfront-origins';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as route53 from 'aws-cdk-lib/aws-route53';
import * as route53Targets from 'aws-cdk-lib/aws-route53-targets';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { Construct } from 'constructs';

/**
 * Phase 1 of the procurement document: two public EC2 instances plus a
 * private, Single-AZ RDS instance. Redis and RabbitMQ run on the web EC2.
 * There is deliberately no NAT gateway, ALB, NLB, or public database.
 */
export class NiuMaCostSaverStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const gameInstanceType = this.node.tryGetContext('gameInstanceType') ?? 't3.medium';
    const apiDomain = this.node.tryGetContext('apiDomain') ?? '';
    const webDomain = this.node.tryGetContext('webDomain') ?? '';
    const webCertificateArn = this.node.tryGetContext('webCertificateArn') ?? '';
    const webHostedZoneId = this.node.tryGetContext('webHostedZoneId') ?? '';
    const webHostedZoneName = this.node.tryGetContext('webHostedZoneName') ?? '';
    const webUiDomain = this.node.tryGetContext('webUiDomain') ?? '';
    const webUiCertificateArn = this.node.tryGetContext('webUiCertificateArn') ?? '';
    const webUiHostedZoneId = this.node.tryGetContext('webUiHostedZoneId') ?? '';
    const webUiHostedZoneName = this.node.tryGetContext('webUiHostedZoneName') ?? '';
    const webUiApiPrefix = normalizeLeadingSlash(this.node.tryGetContext('webUiApiPrefix') ?? '/niuma66');
    const webUiStaticPrefix = normalizePathSegment(this.node.tryGetContext('webUiStaticPrefix') ?? 'niuma66-ui');

    if (!apiDomain) {
      throw new Error('apiDomain 必须设置；Web UI CloudFront 需要用它代理后台 API。');
    }
    if (Boolean(webDomain) !== Boolean(webCertificateArn)) {
      throw new Error('webDomain 与 webCertificateArn 必须同时设置；未设置时将仅创建 CloudFront 默认域名。');
    }
    if (webHostedZoneId && (!webDomain || !webHostedZoneName)) {
      throw new Error('设置 webHostedZoneId 时，还必须设置 webDomain 和 webHostedZoneName。');
    }
    if (Boolean(webUiDomain) !== Boolean(webUiCertificateArn)) {
      throw new Error('webUiDomain 与 webUiCertificateArn 必须同时设置；未设置时将仅创建 CloudFront 默认域名。');
    }
    if (webUiHostedZoneId && (!webUiDomain || !webUiHostedZoneName)) {
      throw new Error('设置 webUiHostedZoneId 时，还必须设置 webUiDomain 和 webUiHostedZoneName。');
    }

    Tags.of(this).add('Application', 'niuma');
    Tags.of(this).add('Environment', 'production');
    Tags.of(this).add('Architecture', 'cost-saver');

    const vpc = new ec2.Vpc(this, 'Vpc', {
      maxAzs: 2,
      natGateways: 0,
      subnetConfiguration: [
        { name: 'public', subnetType: ec2.SubnetType.PUBLIC, cidrMask: 24 },
        { name: 'database', subnetType: ec2.SubnetType.PRIVATE_ISOLATED, cidrMask: 24 },
      ],
    });

    const webSg = new ec2.SecurityGroup(this, 'WebSecurityGroup', {
      vpc,
      description: 'Internet edge for HTTPS API only; administration uses SSM.',
      allowAllOutbound: true,
    });
    webSg.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(80), 'ACME HTTP-01 challenge');
    webSg.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(443), 'Public HTTPS API');

    const gameSg = new ec2.SecurityGroup(this, 'GameSecurityGroup', {
      vpc,
      description: 'Public game endpoints and private access to self-hosted middleware.',
      allowAllOutbound: true,
    });
    gameSg.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(80), 'ACME HTTP-01 challenge');
    gameSg.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(9098), 'Public WSS game endpoint');
    gameSg.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(10086), 'Public raw TCP game endpoint');

    // The Java machine hosts the economical Redis/RabbitMQ pair. These ports
    // are never opened to the internet, only to the game instance security group.
    webSg.addIngressRule(gameSg, ec2.Port.tcp(6379), 'Game server to Redis');
    webSg.addIngressRule(gameSg, ec2.Port.tcp(5672), 'Game server to RabbitMQ');

    const dbSg = new ec2.SecurityGroup(this, 'DatabaseSecurityGroup', {
      vpc,
      description: 'MySQL is reachable only from application instances.',
      allowAllOutbound: false,
    });
    dbSg.addIngressRule(webSg, ec2.Port.tcp(3306), 'Web server to RDS MySQL');
    dbSg.addIngressRule(gameSg, ec2.Port.tcp(3306), 'Game server to RDS MySQL');

    const artifactBucket = new s3.Bucket(this, 'ArtifactBucket', {
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      encryption: s3.BucketEncryption.S3_MANAGED,
      enforceSSL: true,
      versioned: true,
      removalPolicy: RemovalPolicy.RETAIN,
      autoDeleteObjects: false,
      lifecycleRules: [{ prefix: 'releases/', expiration: Duration.days(90) }],
    });

    // The browser client has its own bucket. It must never reuse the artifact
    // bucket because CloudFront is the only public read path for game assets.
    const webClientBucket = new s3.Bucket(this, 'WebClientBucket', {
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      encryption: s3.BucketEncryption.S3_MANAGED,
      enforceSSL: true,
      versioned: true,
      removalPolicy: RemovalPolicy.RETAIN,
      autoDeleteObjects: false,
    });

    const webUiBucket = new s3.Bucket(this, 'WebUiBucket', {
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      encryption: s3.BucketEncryption.S3_MANAGED,
      enforceSSL: true,
      versioned: true,
      removalPolicy: RemovalPolicy.RETAIN,
      autoDeleteObjects: false,
    });

    const webCertificate = webCertificateArn
      ? acm.Certificate.fromCertificateArn(this, 'WebClientCertificate', webCertificateArn)
      : undefined;
    const webClientDistribution = new cloudfront.Distribution(this, 'WebClientDistribution', {
      comment: 'NiuMa Cocos web client',
      defaultRootObject: 'index.html',
      certificate: webCertificate,
      domainNames: webCertificate ? [webDomain] : undefined,
      enableIpv6: true,
      defaultBehavior: {
        origin: origins.S3BucketOrigin.withOriginAccessControl(webClientBucket),
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        allowedMethods: cloudfront.AllowedMethods.ALLOW_GET_HEAD,
        cachedMethods: cloudfront.CachedMethods.CACHE_GET_HEAD,
        cachePolicy: cloudfront.CachePolicy.CACHING_OPTIMIZED,
        responseHeadersPolicy: cloudfront.ResponseHeadersPolicy.SECURITY_HEADERS,
        compress: true,
      },
    });

    if (webHostedZoneId) {
      const hostedZone = route53.HostedZone.fromHostedZoneAttributes(this, 'WebClientHostedZone', {
        hostedZoneId: webHostedZoneId,
        zoneName: webHostedZoneName,
      });
      const normalizedZoneName = webHostedZoneName.replace(/\.$/, '');
      const recordName = webDomain === normalizedZoneName
        ? undefined
        : webDomain.endsWith(`.${normalizedZoneName}`)
          ? webDomain.slice(0, -(normalizedZoneName.length + 1))
          : webDomain;
      const target = route53.RecordTarget.fromAlias(new route53Targets.CloudFrontTarget(webClientDistribution));
      new route53.ARecord(this, 'WebClientAliasRecord', { zone: hostedZone, recordName, target });
      new route53.AaaaRecord(this, 'WebClientAliasIpv6Record', { zone: hostedZone, recordName, target });
    }

    const webUiIndexRewriteFunction = new cloudfront.Function(this, 'WebUiIndexRewriteFunction', {
      code: cloudfront.FunctionCode.fromInline(`
function handler(event) {
  var request = event.request;
  var prefix = ${JSON.stringify(`/${webUiStaticPrefix}`)};
  if (request.uri === '/' || request.uri === prefix || request.uri === prefix + '/') {
    request.uri = prefix + '/index.html';
  } else if (request.uri.indexOf(prefix + '/') === 0 && request.uri.indexOf('.', prefix.length + 1) === -1) {
    request.uri = prefix + '/index.html';
  }
  return request;
}
`),
    });
    const webUiApiRewriteFunction = new cloudfront.Function(this, 'WebUiApiRewriteFunction', {
      code: cloudfront.FunctionCode.fromInline(`
function handler(event) {
  var request = event.request;
  var prefix = ${JSON.stringify(webUiApiPrefix)};
  if (request.uri === prefix) {
    request.uri = '/';
  } else if (request.uri.indexOf(prefix + '/') === 0) {
    request.uri = request.uri.substring(prefix.length);
  }
  return request;
}
`),
    });
    const webUiCertificate = webUiCertificateArn
      ? acm.Certificate.fromCertificateArn(this, 'WebUiCertificate', webUiCertificateArn)
      : undefined;
    const webUiDistribution = new cloudfront.Distribution(this, 'WebUiDistribution', {
      comment: 'NiuMa admin web UI',
      defaultRootObject: `${webUiStaticPrefix}/index.html`,
      certificate: webUiCertificate,
      domainNames: webUiCertificate ? [webUiDomain] : undefined,
      enableIpv6: true,
      defaultBehavior: {
        origin: origins.S3BucketOrigin.withOriginAccessControl(webUiBucket),
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        allowedMethods: cloudfront.AllowedMethods.ALLOW_GET_HEAD,
        cachedMethods: cloudfront.CachedMethods.CACHE_GET_HEAD,
        cachePolicy: cloudfront.CachePolicy.CACHING_OPTIMIZED,
        responseHeadersPolicy: cloudfront.ResponseHeadersPolicy.SECURITY_HEADERS,
        compress: true,
        functionAssociations: [{
          eventType: cloudfront.FunctionEventType.VIEWER_REQUEST,
          function: webUiIndexRewriteFunction,
        }],
      },
      additionalBehaviors: {
        [`${webUiApiPrefix.replace(/^\//, '')}/*`]: {
          origin: new origins.HttpOrigin(apiDomain, {
            protocolPolicy: cloudfront.OriginProtocolPolicy.HTTPS_ONLY,
            readTimeout: Duration.seconds(60),
          }),
          viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
          allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
          cachedMethods: cloudfront.CachedMethods.CACHE_GET_HEAD_OPTIONS,
          cachePolicy: cloudfront.CachePolicy.CACHING_DISABLED,
          originRequestPolicy: cloudfront.OriginRequestPolicy.ALL_VIEWER_EXCEPT_HOST_HEADER,
          responseHeadersPolicy: cloudfront.ResponseHeadersPolicy.SECURITY_HEADERS,
          compress: true,
          functionAssociations: [{
            eventType: cloudfront.FunctionEventType.VIEWER_REQUEST,
            function: webUiApiRewriteFunction,
          }],
        },
      },
    });

    if (webUiHostedZoneId) {
      const hostedZone = route53.HostedZone.fromHostedZoneAttributes(this, 'WebUiHostedZone', {
        hostedZoneId: webUiHostedZoneId,
        zoneName: webUiHostedZoneName,
      });
      const normalizedZoneName = webUiHostedZoneName.replace(/\.$/, '');
      const recordName = webUiDomain === normalizedZoneName
        ? undefined
        : webUiDomain.endsWith(`.${normalizedZoneName}`)
          ? webUiDomain.slice(0, -(normalizedZoneName.length + 1))
          : webUiDomain;
      const target = route53.RecordTarget.fromAlias(new route53Targets.CloudFrontTarget(webUiDistribution));
      new route53.ARecord(this, 'WebUiAliasRecord', { zone: hostedZone, recordName, target });
      new route53.AaaaRecord(this, 'WebUiAliasIpv6Record', { zone: hostedZone, recordName, target });
    }

    const gameRepository = new ecr.Repository(this, 'GameRepository', {
      imageScanOnPush: true,
      removalPolicy: RemovalPolicy.RETAIN,
      lifecycleRules: [{ maxImageCount: 10, description: 'Keep the latest ten game images' }],
    });

    const redisSecret = new secretsmanager.Secret(this, 'RedisSecret', {
      description: 'NiuMa Redis password',
      removalPolicy: RemovalPolicy.RETAIN,
      generateSecretString: {
        secretStringTemplate: '{}',
        generateStringKey: 'password',
        passwordLength: 32,
        excludePunctuation: true,
      },
    });
    const rabbitSecret = new secretsmanager.Secret(this, 'RabbitSecret', {
      description: 'NiuMa RabbitMQ password for user niuma',
      removalPolicy: RemovalPolicy.RETAIN,
      generateSecretString: {
        secretStringTemplate: '{}',
        generateStringKey: 'password',
        passwordLength: 32,
        excludePunctuation: true,
      },
    });
    const appSecret = new secretsmanager.Secret(this, 'ApplicationSecret', {
      description: 'NiuMa JWT token signing secret',
      removalPolicy: RemovalPolicy.RETAIN,
      generateSecretString: {
        secretStringTemplate: '{}',
        generateStringKey: 'tokenSecret',
        passwordLength: 48,
        excludePunctuation: true,
      },
    });

    const database = new rds.DatabaseInstance(this, 'Database', {
      // ap-east-1 currently offers MySQL 8.0.46; pinning it keeps regional deployments reproducible.
      engine: rds.DatabaseInstanceEngine.mysql({ version: rds.MysqlEngineVersion.VER_8_0_46 }),
      instanceType: new ec2.InstanceType('t4g.medium'),
      credentials: rds.Credentials.fromGeneratedSecret('niuma_admin'),
      databaseName: 'niuma',
      vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
      securityGroups: [dbSg],
      publiclyAccessible: false,
      multiAz: false,
      allocatedStorage: 20,
      maxAllocatedStorage: 100,
      storageType: rds.StorageType.GP3,
      storageEncrypted: true,
      backupRetention: Duration.days(7),
      deleteAutomatedBackups: false,
      deletionProtection: true,
      removalPolicy: RemovalPolicy.RETAIN,
      copyTagsToSnapshot: true,
      cloudwatchLogsExports: ['error', 'general', 'slowquery'],
      monitoringInterval: Duration.seconds(60),
      enablePerformanceInsights: true,
    });
    database.secret?.applyRemovalPolicy(RemovalPolicy.RETAIN);

    const instancePolicies = [
      iam.ManagedPolicy.fromAwsManagedPolicyName('AmazonSSMManagedInstanceCore'),
      iam.ManagedPolicy.fromAwsManagedPolicyName('CloudWatchAgentServerPolicy'),
    ];
    const webRole = new iam.Role(this, 'WebInstanceRole', {
      assumedBy: new iam.ServicePrincipal('ec2.amazonaws.com'),
      managedPolicies: instancePolicies,
    });
    const gameRole = new iam.Role(this, 'GameInstanceRole', {
      assumedBy: new iam.ServicePrincipal('ec2.amazonaws.com'),
      managedPolicies: [
        ...instancePolicies,
        iam.ManagedPolicy.fromAwsManagedPolicyName('AmazonEC2ContainerRegistryReadOnly'),
      ],
    });

    artifactBucket.grantRead(webRole);
    artifactBucket.grantRead(gameRole);
    artifactBucket.grantPut(gameRole, 'releases/*');
    database.secret?.grantRead(webRole);
    database.secret?.grantRead(gameRole);
    redisSecret.grantRead(webRole);
    redisSecret.grantRead(gameRole);
    rabbitSecret.grantRead(webRole);
    rabbitSecret.grantRead(gameRole);
    appSecret.grantRead(webRole);
    gameRepository.grantPullPush(gameRole);

    const linux = ec2.MachineImage.latestAmazonLinux2023();
    const commonInstanceProps = {
      vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
      machineImage: linux,
      requireImdsv2: true,
      blockDevices: [{
        deviceName: '/dev/xvda',
        volume: ec2.BlockDeviceVolume.ebs(30, {
          encrypted: true,
          volumeType: ec2.EbsDeviceVolumeType.GP3,
          deleteOnTermination: false,
        }),
      }],
    };

    const web = new ec2.Instance(this, 'WebInstance', {
      ...commonInstanceProps,
      instanceType: new ec2.InstanceType('t3.medium'),
      securityGroup: webSg,
      role: webRole,
      instanceName: 'niuma-web',
    });
    const game = new ec2.Instance(this, 'GameInstance', {
      ...commonInstanceProps,
      instanceType: new ec2.InstanceType(gameInstanceType),
      securityGroup: gameSg,
      role: gameRole,
      instanceName: 'niuma-game',
    });

    const webEip = new ec2.CfnEIP(this, 'WebEip', { domain: 'vpc' });
    const gameEip = new ec2.CfnEIP(this, 'GameEip', { domain: 'vpc' });
    new ec2.CfnEIPAssociation(this, 'WebEipAssociation', {
      allocationId: webEip.attrAllocationId,
      instanceId: web.instanceId,
    });
    new ec2.CfnEIPAssociation(this, 'GameEipAssociation', {
      allocationId: gameEip.attrAllocationId,
      instanceId: game.instanceId,
    });

    const outputs: Record<string, string> = {
      ArtifactBucketName: artifactBucket.bucketName,
      WebClientBucketName: webClientBucket.bucketName,
      WebClientDistributionId: webClientDistribution.distributionId,
      WebClientDistributionDomainName: webClientDistribution.distributionDomainName,
      WebClientUrl: webCertificate ? `https://${webDomain}` : `https://${webClientDistribution.distributionDomainName}`,
      WebUiBucketName: webUiBucket.bucketName,
      WebUiDistributionId: webUiDistribution.distributionId,
      WebUiDistributionDomainName: webUiDistribution.distributionDomainName,
      WebUiUrl: webUiCertificate ? `https://${webUiDomain}` : `https://${webUiDistribution.distributionDomainName}`,
      WebUiStaticPrefix: webUiStaticPrefix,
      WebUiApiPrefix: webUiApiPrefix,
      GameEcrRepositoryUri: gameRepository.repositoryUri,
      WebInstanceId: web.instanceId,
      GameInstanceId: game.instanceId,
      WebElasticIp: webEip.attrPublicIp,
      GameElasticIp: gameEip.attrPublicIp,
      WebPrivateIp: web.instancePrivateIp,
      DatabaseEndpoint: database.dbInstanceEndpointAddress,
      DatabaseSecretArn: database.secret?.secretArn ?? '',
      RedisSecretArn: redisSecret.secretArn,
      RabbitSecretArn: rabbitSecret.secretArn,
      ApplicationSecretArn: appSecret.secretArn,
    };
    for (const [key, value] of Object.entries(outputs)) {
      new cdk.CfnOutput(this, key, { value });
    }
  }
}

function normalizeLeadingSlash(value: string): string {
  const trimmed = value.trim().replace(/\/+$/g, '');
  if (!trimmed || trimmed === '/') {
    throw new Error('webUiApiPrefix 必须是类似 /niuma66 的非根路径。');
  }
  return trimmed.startsWith('/') ? trimmed : `/${trimmed}`;
}

function normalizePathSegment(value: string): string {
  const trimmed = value.trim().replace(/^\/+|\/+$/g, '');
  if (!trimmed || trimmed.includes('/')) {
    throw new Error('webUiStaticPrefix 必须是单层路径，例如 niuma66-ui。');
  }
  return trimmed;
}
