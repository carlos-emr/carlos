//CHECKSTYLE:OFF
/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */
package ca.openosp.openo.billings.ca.on.pageUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;


import ca.openosp.openo.commn.dao.BillingONPaymentDao;
import ca.openosp.openo.commn.dao.BillingPaymentTypeDao;

import ca.openosp.openo.commn.model.BillingPaymentType;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class PaymentType2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private BillingPaymentTypeDao billingPaymentTypeDao = SpringUtils.getBean(BillingPaymentTypeDao.class);
    private BillingONPaymentDao billPaymentDao = SpringUtils.getBean(BillingONPaymentDao.class);

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String execute() {
        String method = request.getParameter("method");
        if ("listAllType".equals(method)) {
            return listAllType();
        } else if ("createType".equals(method)) {
            return createType();
        } else if ("editType".equals(method)) {
            return editType();
        } else if ("removeType".equals(method)) {
            return removeType();
        }

        return listAllType();
    }

    public String listAllType() {
        try {
            List<BillingPaymentType> paymentTypeList = billingPaymentTypeDao.findAll();
            request.setAttribute("paymentTypeList", paymentTypeList);
            return SUCCESS;
        } catch (Exception e) {
            e.printStackTrace();
            return ERROR;
        }
    }

    public String createType() {

        String paymentType = request.getParameter("paymentType");
        if (null != paymentType && !paymentType.isEmpty()) {
            BillingPaymentType billingPaymentType = null;
            Map<String, String> retMap = new HashMap<String, String>();
            ObjectNode json = null;
            try {
                billingPaymentType = billingPaymentTypeDao.getPaymentTypeByName(paymentType);

                if (billingPaymentType != null) {
                    retMap.put("ret", "1");
                    retMap.put("reason", "Payment type: " + paymentType + " already exists!");
                } else {
                    billingPaymentType = new BillingPaymentType();
                    billingPaymentType.setPaymentType(paymentType);
                    billingPaymentTypeDao.persist(billingPaymentType);
                    retMap.put("ret", "0");
                    //return actionMapping.findForward("success");
                }
            } catch (Exception e) {
                retMap.put("ret", "1");
                retMap.put("reason", e.toString());
            }

            try {
                json = objectMapper.valueToTree(retMap);
                response.getWriter().write(json.toString());
            } catch (IOException e) {
                // TODO Auto-generated catch block
                MiscUtils.getLogger().info(e.toString());
            }
        }

        return null;
    }

    public String editType() {
        String oldPaymentType = request.getParameter("oldPaymentType");
        String paymentType = request.getParameter("paymentType");
        if (oldPaymentType != null && !oldPaymentType.isEmpty() && null != paymentType && !paymentType.isEmpty()) {
            BillingPaymentType old = null;
            Map<String, String> retMap = new HashMap<String, String>();
            ObjectNode json = null;
            try {
                old = billingPaymentTypeDao.getPaymentTypeByName(oldPaymentType);
                if (old == null) {
                    retMap.put("ret", "1");
                    retMap.put("reason", "Old payment type: " + oldPaymentType + " doesn't exist!");
                } else {
                    BillingPaymentType newType = billingPaymentTypeDao.getPaymentTypeByName(paymentType);
                    if (newType != null) {
                        retMap.put("ret", "1");
                        retMap.put("reason", "Payment type: " + paymentType + " already exists!");
                    } else {
                        old.setPaymentType(paymentType);
                        billingPaymentTypeDao.merge(old);
                        retMap.put("ret", "0");
                    }
                }
            } catch (Exception e) {
                retMap.put("ret", "1");
                retMap.put("reason", e.toString());
            }

            try {
                json = objectMapper.valueToTree(retMap);
                response.getWriter().write(json.toString());
            } catch (IOException e) {
                // TODO Auto-generated catch block
                MiscUtils.getLogger().info(e.toString());
            }
        }

        return null;
    }

    public String removeType() {
        int paymentTypeId = 0;
        ObjectNode ret = objectMapper.createObjectNode();
        try {
            paymentTypeId = Integer.parseInt(request.getParameter("paymentTypeId"));
        } catch (Exception e) {
            MiscUtils.getLogger().info(e.toString());
            ret.put("ret", 1);
            ret.put("reason", e.toString());
        }
        if (paymentTypeId != 0) {
            int count = billPaymentDao.getCountOfPaymentByPaymentTypeId(paymentTypeId);
            if (count == 0) {
                billingPaymentTypeDao.remove(paymentTypeId);
                ret.put("ret", 0);
            } else {
                ret.put("ret", 1);
                ret.put("reason", "This payment type has been used in some payment!");
            }
        }

        response.setCharacterEncoding("utf-8");
        response.setContentType("html/text");
        try {
            response.getWriter().print(ret);
            response.getWriter().flush();
            response.getWriter().close();
        } catch (Exception e) {
            MiscUtils.getLogger().info(e.toString());
        }

        return null;
    }
}
