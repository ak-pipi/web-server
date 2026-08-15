-- v35_decimal_gold_and_cash_pledge.sql
-- Allow PaoDeKuai settlement/carry scores to keep one decimal place.

ALTER TABLE `capital`
  MODIFY COLUMN `gold` decimal(18,1) NOT NULL DEFAULT '0.0' COMMENT '金币数量，支持一位小数积分',
  MODIFY COLUMN `deposit` decimal(18,1) NOT NULL DEFAULT '0.0' COMMENT '银行金币存款，支持一位小数积分';

ALTER TABLE `cash_pledge`
  MODIFY COLUMN `amount` decimal(18,1) NOT NULL COMMENT '押金数额，支持一位小数积分';
