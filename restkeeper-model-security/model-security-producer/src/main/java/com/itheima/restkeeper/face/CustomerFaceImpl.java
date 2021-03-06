package com.itheima.restkeeper.face;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itheima.restkeeper.AffixFace;
import com.itheima.restkeeper.CustomerFace;
import com.itheima.restkeeper.enums.CustomerEnum;
import com.itheima.restkeeper.exception.ProjectException;
import com.itheima.restkeeper.pojo.Customer;
import com.itheima.restkeeper.pojo.Role;
import com.itheima.restkeeper.req.AffixVo;
import com.itheima.restkeeper.req.CustomerVo;
import com.itheima.restkeeper.service.ICustomerAdapterService;
import com.itheima.restkeeper.service.ICustomerService;
import com.itheima.restkeeper.utils.BeanConv;
import com.itheima.restkeeper.utils.EmptyUtil;
import com.itheima.restkeeper.utils.ExceptionsUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.dubbo.config.annotation.Method;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @ClassName CustomerFaceImpl.java
 * @Description TODO
 */
@Slf4j
@DubboService(version = "${dubbo.application.version}",timeout = 5000,
    methods ={
        @Method(name = "findCustomerVoPage",retries = 2),
        @Method(name = "createCustomer",retries = 0),
        @Method(name = "updateCustomer",retries = 0),
        @Method(name = "deleteCustomer",retries = 0),
        @Method(name = "updateCustomerEnableFlag",retries = 0)
    })
public class CustomerFaceImpl implements CustomerFace {

    @Autowired
    BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    ICustomerService customerService;

    @Autowired
    ICustomerAdapterService customerAdapterService;

    @DubboReference(version = "${dubbo.application.version}",check = false)
    AffixFace affixFace;

    @Override
    public Page<CustomerVo> findCustomerVoPage(CustomerVo customerVo,
                                               int pageNum,
                                               int pageSize)throws ProjectException {
        try {
            Page<Customer> page = customerService.findCustomerVoPage(customerVo, pageNum, pageSize);
            Page<CustomerVo> pageVo = new Page<>();
            BeanConv.toBean(page,pageVo);
            //???????????????
            List<Customer> userList = page.getRecords();
            List<CustomerVo> customerVoList = BeanConv.toBeanList(userList,CustomerVo.class);
            if (!EmptyUtil.isNullOrEmpty(userList)){
                customerVoList.forEach(n->{
                    //????????????
                    List<AffixVo> affixVoList = affixFace.findAffixVoByBusinessId(n.getId());
                    if (!EmptyUtil.isNullOrEmpty(affixVoList)){
                        n.setAffixVo(affixVoList.get(0));
                    }
                    //????????????
                    List<Role> roles = customerAdapterService.findRoleByCustomerId(n.getId());
                    List<String> roleIdList = new ArrayList<>();
                    for (Role role : roles) {
                        roleIdList.add(String.valueOf(role.getId()));
                    }
                    String[] roleIds = new String[roleIdList.size()];
                    roleIdList.toArray(roleIds);
                    n.setHasRoleIds(roleIds);
                });
            }
            pageVo.setRecords(customerVoList);
            return pageVo;
        } catch (Exception e) {
            log.error("???????????????????????????{}", ExceptionsUtil.getStackTraceAsString(e));
            throw new ProjectException(CustomerEnum.PAGE_FAIL);
        }
    }

    @Override
    public CustomerVo createCustomer(CustomerVo customerVo)throws ProjectException {
        try {
            String plainPassword = customerVo.getPassword();
            //????????????{bcrypt}?????????????????????
            String password = "{bcrypt}"+bCryptPasswordEncoder.encode(plainPassword);
            customerVo.setPassword(password);
            CustomerVo customerVoResult = BeanConv.toBean(customerService.createCustomer(customerVo), CustomerVo.class);
            //????????????
            if (!EmptyUtil.isNullOrEmpty(customerVoResult)){
                affixFace.bindBusinessId(AffixVo.builder().businessId(customerVoResult.getId()).id(customerVo.getAffixVo().getId()).build());
            }
            customerVoResult.setAffixVo(AffixVo.builder()
                    .pathUrl(customerVo.getAffixVo().getPathUrl())
                    .businessId(customerVoResult.getId())
                    .id(customerVo.getAffixVo().getId()).build());
            //????????????
            customerVoResult.setHasRoleIds(customerVo.getHasRoleIds());
            return customerVoResult;
        } catch (Exception e) {
            log.error("?????????????????????{}", ExceptionsUtil.getStackTraceAsString(e));
            throw new ProjectException(CustomerEnum.CREATE_FAIL);
        }

    }

    @Override
    public Boolean updateCustomer(CustomerVo customerVo)throws ProjectException {
        try {
            Boolean flag = customerService.updateCustomer(customerVo);
            if (flag){
                List<AffixVo> affixVoList = affixFace.findAffixVoByBusinessId(customerVo.getId());
                List<Long> affixIds = affixVoList.stream().map(AffixVo::getId).collect(Collectors.toList());
                if (!affixIds.contains(customerVo.getAffixVo().getId())){
                    //????????????
                    flag = affixFace.deleteAffixVoByBusinessId(customerVo.getId());
                    //???????????????
                    affixFace.bindBusinessId(AffixVo.builder()
                        .businessId(customerVo.getId())
                        .id(customerVo.getAffixVo().getId())
                        .build());
                }
            }
            return flag;
        } catch (Exception e) {
            log.error("?????????????????????{}", ExceptionsUtil.getStackTraceAsString(e));
            throw new ProjectException(CustomerEnum.UPDATE_FAIL);
        }
    }

    @Override
    public Boolean deleteCustomer(String[] checkedIds)throws ProjectException {
        try {
            Boolean flag = customerService.deleteCustomer(checkedIds);
            if (flag){
                //????????????
                for (String checkedId : checkedIds) {
                    affixFace.deleteAffixVoByBusinessId(Long.valueOf(checkedId));
                }
            }
            return flag;
        } catch (Exception e) {
            log.error("?????????????????????{}", ExceptionsUtil.getStackTraceAsString(e));
            throw new ProjectException(CustomerEnum.DELETE_FAIL);
        }

    }

    @Override
    public CustomerVo findCustomerByCustomerId(Long userId)throws ProjectException {
        try {
            Customer user = customerService.getById(userId);
            if (!EmptyUtil.isNullOrEmpty(user)){
                return BeanConv.toBean(user,CustomerVo.class);
            }
            return null;
        } catch (Exception e) {
            log.error("?????????????????????????????????{}", ExceptionsUtil.getStackTraceAsString(e));
            throw new ProjectException(CustomerEnum.SELECT_USER_FAIL);
        }
    }

    @Override
    public List<CustomerVo> findCustomerVoList()throws ProjectException {
        try {
            return BeanConv.toBeanList(customerService.findCustomerVoList(),CustomerVo.class);
        } catch (Exception e) {
            log.error("????????????list?????????{}", ExceptionsUtil.getStackTraceAsString(e));
            throw new ProjectException(CustomerEnum.SELECT_USER_LIST_FAIL);
        }
    }

    @Override
    public Boolean updateCustomerEnableFlag(CustomerVo customerVo)throws ProjectException {
        try {
            return customerService.updateCustomerEnableFlag(customerVo);
        } catch (Exception e) {
            log.error("???????????????????????????{}", ExceptionsUtil.getStackTraceAsString(e));
            throw new ProjectException(CustomerEnum.UPDATE_FAIL);
        }

    }
}
