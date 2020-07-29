package com.platform.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

public class RechargeVo implements Serializable {
    // 充值记录id
    private Long id;
    //会员ID
    private Long userId;
    //类型（0: 消费  1: 充值）
    private Integer type;
    //商户订单号
    private String out_trade_no;
    //微信支付订单号
    private String transaction_id;
    //单次充值金额
    private BigDecimal amount;
    // 充值时间
//    private Date rechargeTime;
    private String rechargeTime;

    public void setId(Long id) {
        this.id = id;
    }
    public Long getId() {
        return id;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
    public Long getUserId() {
        return userId;
    }

    public void setType(Integer type) {
        this.type = type;
    }
    public Integer getType() {
        return type;
    }

    public void setOut_trade_no(String out_trade_no) {
        this.out_trade_no = out_trade_no;
    }
    public String getOut_trade_no() { return out_trade_no; }

    public void setTransaction_id(String transaction_id) {
        this.transaction_id = transaction_id;
    }
    public String getTransaction_id() { return transaction_id; }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
    public BigDecimal getAmount() {
        return amount;
    }

    public void setRechargeTime(String rechargeTime) {
        this.rechargeTime = rechargeTime;
    }
    public String getRechargeTime() {
        return rechargeTime;
    }
}
