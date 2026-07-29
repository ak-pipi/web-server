#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import { NiuMaCostSaverStack } from '../lib/niuma-stack';

const app = new cdk.App();

new NiuMaCostSaverStack(app, process.env.STACK_NAME ?? 'NiuMaCostSaver', {
  description: 'NiuMa 1000 PCU cost-saver infrastructure',
});
