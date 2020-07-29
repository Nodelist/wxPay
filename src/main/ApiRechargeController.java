package com.platform.api;


import com.alibaba.fastjson.JSONObject;
import com.platform.annotation.IgnoreAuth;
import com.platform.annotation.LoginUser;
import com.platform.entity.OrderVo;
import com.platform.entity.RechargeVo;
import com.platform.entity.UserVo;
import com.platform.service.ApiRechargeService;
import com.platform.service.ApiUserService;
import com.platform.util.ApiBaseAction;
import com.platform.util.ApiPageUtils;
import com.platform.util.RedisUtils;
import com.platform.util.wechat.WechatRefundApiResult;
import com.platform.util.wechat.WechatUtil;
import com.platform.utils.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.models.auth.In;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author WangQ
 * @date 2020-7-13
 */
@Api(tags = "充值相关")
@RestController
@RequestMapping("/api/recharge")
public class ApiRechargeController extends ApiBaseAction {
    @Autowired
    private ApiRechargeService rechargeService;
    @Autowired
    private ApiUserService userService;


    /**
     * 获取订单列表
     */
    @ApiOperation(value = "获取订单列表")
    @RequestMapping("list")
    public Object list(@LoginUser UserVo loginUser,
                       @RequestParam(value = "page", defaultValue = "1") Integer page,
                       @RequestParam(value = "size", defaultValue = "10") Integer size) {
        //
        Map params = new HashMap();
        params.put("user_id", loginUser.getUserId());
        params.put("page", page);
        params.put("limit", size);
        params.put("sidx", "id");
        params.put("order", "desc");
        //查询列表数据
        Query query = new Query(params);
        List<RechargeVo> orderEntityList = rechargeService.queryList(query);
        int total = rechargeService.queryTotal(query);
        ApiPageUtils pageUtil = new ApiPageUtils(orderEntityList, total, query.getLimit(), query.getPage());
        return toResponsSuccess(pageUtil);
    }

    /**
     * 生成微信支付所需参数
     */
    @ApiOperation(value = "充值提交")
    @PostMapping("submit")
    public Object submit(@LoginUser UserVo loginUser) {
        BigDecimal amount = this.getJsonRequest().getBigDecimal("amount");
//        BigDecimal totalFee = amount.multiply(new BigDecimal(100)).intValue();
        Integer totalFee = amount.multiply(new BigDecimal(100)).intValue();
        Map<Object, Object> resultObj = new TreeMap();
        String nonceStr = CharUtil.getRandomString(32);
        try {
            Map<Object, Object> parame = new TreeMap<Object, Object>();
            parame.put("appid", ResourceUtil.getConfigByName("wx.appId"));
            // 商家账号。
            parame.put("mch_id", ResourceUtil.getConfigByName("wx.mchId"));
            String randomStr = CharUtil.getRandomNum(18).toUpperCase();
            // 随机字符串
            parame.put("nonce_str", randomStr);
            // 商户订单编号
            parame.put("out_trade_no", getTradeNo());
            // 商品描述
            parame.put("body", "充值单");
            //支付金额
            parame.put("total_fee", totalFee);
            // 回调地址
            parame.put("notify_url", ResourceUtil.getConfigByName("wx.notifyUrl"));
            // 交易类型APP
            parame.put("trade_type", ResourceUtil.getConfigByName("wx.tradeType"));
            parame.put("spbill_create_ip", getClientIp());
            parame.put("openid", loginUser.getWeixin_openid());
            String sign = WechatUtil.arraySign(parame, ResourceUtil.getConfigByName("wx.paySignKey"));
            // 数字签证
            parame.put("sign", sign);

            String xml = com.platform.utils.MapUtils.convertMap2Xml(parame);
            Map<String, Object> resultUn = XmlUtil.xmlStrToMap(WechatUtil.requestOnce(ResourceUtil.getConfigByName("wx.uniformorder"), xml));
            // 响应报文
            String return_code = com.platform.utils.MapUtils.getString("return_code", resultUn);
            String return_msg = com.platform.utils.MapUtils.getString("return_msg", resultUn);
            //
            if (return_code.equalsIgnoreCase("FAIL")) {
                return toResponsFail("支付失败," + return_msg);
            } else if (return_code.equalsIgnoreCase("SUCCESS")) {
                // 返回数据
                String result_code = com.platform.utils.MapUtils.getString("result_code", resultUn);
                String err_code_des = com.platform.utils.MapUtils.getString("err_code_des", resultUn);
                if (result_code.equalsIgnoreCase("FAIL")) {
                    return toResponsFail("支付失败," + err_code_des);
                } else if (result_code.equalsIgnoreCase("SUCCESS")) {
                    String prepay_id = MapUtils.getString("prepay_id", resultUn);
                    // 先生成paySign 参考https://pay.weixin.qq.com/wiki/doc/api/wxa/wxa_api.php?chapter=7_7&index=5
                    resultObj.put("appId", ResourceUtil.getConfigByName("wx.appId"));
                    //resultObj.put("timeStamp", DateUtils.timeToStr(System.currentTimeMillis() / 1000, DateUtils.DATE_TIME_PATTERN));
                    resultObj.put("timeStamp", System.currentTimeMillis() / 1000 + "");
                    resultObj.put("nonceStr", nonceStr);
                    resultObj.put("package", "prepay_id=" + prepay_id);
                    resultObj.put("signType", "MD5");
                    String paySign = WechatUtil.arraySign(resultObj, ResourceUtil.getConfigByName("wx.paySignKey"));
                    resultObj.put("paySign", paySign);
                    resultObj.put("amount", amount);

                    return toResponsObject(0, "微信统一订单下单成功", resultObj);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return toResponsFail("下单失败,error=" + e.getMessage());
        }
        return toResponsFail("下单失败");
    }

    /**
     * 新增充值记录
     */
    @ApiOperation(value = "新增充值记录")
    @PostMapping("add")
    public Object add(@LoginUser UserVo loginUser) {
        JSONObject params = this.getJsonRequest();
        Integer type = params.getInteger("type");
        String prepayId = params.getString("prepayId");
        BigDecimal amount = params.getBigDecimal("amount");
        RechargeVo recharge = new RechargeVo();
        recharge.setUserId(loginUser.getUserId());
        recharge.setType(type);
        recharge.setTransaction_id(prepayId);
        recharge.setAmount(amount);
        Date date = new Date();
        Timestamp timeStamep = new Timestamp(date.getTime());
        recharge.setRechargeTime(timeStamep.toString());
        rechargeService.save(recharge);
        Map<Object, Object> resultObj = new TreeMap(params);
        BigDecimal a = loginUser.getRechargeAmount();
        // type=0 消费，总金额减
        if (type.equals(0)) {
            loginUser.setRechargeAmount(loginUser.getRechargeAmount().subtract(amount));
        } else if (type.equals(1)){   // 充值 type=1，总金额加
            loginUser.setRechargeAmount(loginUser.getRechargeAmount().add(amount));
        } else {
            return toResponsObject(2, "类型出错", resultObj);
        }
        userService.updateAmount(loginUser);
        resultObj.put("data", recharge);
        return toResponsObject(0, "记录新增成功", resultObj);
    }

    /**
     * 微信订单回调接口(必须部署到公网能访问到的服务器上且关闭防火墙，不能携带参数）
     *
     * @return
     */
    @ApiOperation(value = "微信订单回调接口")
    @RequestMapping(value = "/notify", method = RequestMethod.POST, produces = "text/html;charset=UTF-8")
    @IgnoreAuth
    @ResponseBody
    public void notify(HttpServletRequest request, HttpServletResponse response) {
        System.out.println("----------------进入回调了！！！！！------------------");
        try {
            request.setCharacterEncoding("UTF-8");
            response.setCharacterEncoding("UTF-8");
            response.setContentType("text/html;charset=UTF-8");
            response.setHeader("Access-Control-Allow-Origin", "*");
            InputStream in = request.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len = 0;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            out.close();
            in.close();
            //xml数据
            String reponseXml = new String(out.toByteArray(), "utf-8");

            WechatRefundApiResult result = (WechatRefundApiResult) XmlUtil.xmlStrToBean(reponseXml, WechatRefundApiResult.class);
            System.out.println("----------------" + result.toString()+"------------------");
            String result_code = result.getResult_code();
            if (result_code.equalsIgnoreCase("FAIL")) {
                //订单编号
                String out_trade_no = result.getOut_trade_no();
                logger.error("订单" + out_trade_no + "支付失败");
                response.getWriter().write(setXml("SUCCESS", "OK"));
            } else if (result_code.equalsIgnoreCase("SUCCESS")) {
                Map<Object, Object> retMap = XmlUtil.xmlStrToTreeMap(reponseXml);
                String sign = WechatUtil.arraySign(retMap, ResourceUtil.getConfigByName("wx.paySignKey"));
//                if(!sign.equals(result.getSign())) {//判断签名
//                    System.out.println("----------------签名不匹配！------------------");
//                    return;
//                }
                // 更改订单状态
                // 业务处理
//                Map<String, Object> map = new HashMap<String, Object>();
//                map.put("order_id", result.getOut_trade_no());
                this.saveOrder(result);
                response.getWriter().write(setXml("SUCCESS", "OK"));
                System.out.println("----------------回调完成了！！！------------------");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    /**
     * 获取总金额
     */
    @ApiOperation(value = "获取总金额")
    @RequestMapping("amount")
    public Object queryAmount(@LoginUser UserVo userVo) {
        UserVo obj = userService.queryAmount(userVo.getUserId());
        Map<Object, Object> resultObj = new TreeMap();
        resultObj.put("amount", obj.getRechargeAmount());
        return toResponsObject(0, "success", resultObj);
    }

    // 保存交易订单
    private void saveOrder(WechatRefundApiResult params) {
        System.out.println("----------------进入业务处理方法了！！！------------------");
        System.out.println("----------------" + params.toString()+"------------------");
        BigDecimal amount = new BigDecimal(params.getTotal_fee()).divide(new BigDecimal(100));
        String openId = params.getOpenid();
        UserVo loginUser = userService.queryByOpenId(openId);
        System.out.println("----------------获取当前用户成功了！！！------------------");
        System.out.println("----------------" + loginUser.toString()+"------------------");
        RechargeVo recharge = new RechargeVo();
        recharge.setUserId(loginUser.getUserId());
        recharge.setTransaction_id(params.getTransaction_id());
        recharge.setOut_trade_no(params.getOut_trade_no());
        recharge.setAmount(amount);
        recharge.setRechargeTime(params.getTime_end());
        rechargeService.save(recharge);
        loginUser.setRechargeAmount(loginUser.getRechargeAmount().add(amount));
        userService.updateAmount(loginUser);
        System.out.println("----------------业务处理完了！！！------------------");
    }

    // 生成订单号
    private String getTradeNo() {
        String tradeNo = "";
        SimpleDateFormat sfDate = new SimpleDateFormat("yyyyMMddHHmmss");
        String strDate = sfDate.format(new Date());
        Random rand = new Random();
        int n = 20;
        int randInt = 0;
        for (int i = 0; i < 3; i++) {
            randInt = rand.nextInt(10);
            tradeNo = strDate + randInt;
        }
        return tradeNo;
    }

    //返回微信服务
    public static String setXml(String return_code, String return_msg) {
        return "<xml><return_code><![CDATA[" + return_code + "]]></return_code><return_msg><![CDATA[" + return_msg + "]]></return_msg></xml>";
    }
}
