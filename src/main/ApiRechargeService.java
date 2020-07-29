package com.platform.service;

import com.platform.dao.ApiRechargeMapper;
import com.platform.entity.RechargeVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class ApiRechargeService {
    @Autowired
    private ApiRechargeMapper rechargeDao;

    public List<RechargeVo> queryList(Map<String, Object> map) {
        return rechargeDao.queryList(map);
    }

    public int queryTotal(Map<String, Object> map) {
        return rechargeDao.queryTotal(map);
    }

    public void save(RechargeVo order) {
        rechargeDao.save(order);
    }

    public void delete(Integer id) {
        rechargeDao.delete(id);
    }

    public void deleteBatch(Integer[] ids) {
        rechargeDao.deleteBatch(ids);
    }
}
