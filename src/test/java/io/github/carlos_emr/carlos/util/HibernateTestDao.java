package io.github.carlos_emr.carlos.util;

import java.util.ArrayList;
import java.util.List;


import io.github.carlos_emr.carlos.commn.model.Demographic;
import org.springframework.orm.hibernate5.HibernateTemplate;
import org.hibernate.Session;
import org.springframework.orm.hibernate5.HibernateCallback;
import org.hibernate.HibernateException;

import org.hibernate.query.Query;

import io.github.carlos_emr.carlos.model.security.Secrole;
import io.github.carlos_emr.carlos.commn.model.Provider;

public class HibernateTestDao {
    HibernateTemplate template;

    public void setTemplate(HibernateTemplate template) {
        this.template = template;
    }


    public void findByExample() {

        String sql = "FROM Demographic WHERE last_name = ?";

        try {
            /** 3.1.0*****/
            //List<Secrole> roleNames = template.find("from Secrole");
            //List<String> roleNames =  template.find("select s.roleName from Secrole s");
            //List<Demographic> roleNames = template.find(sql, "TEST");
            //System.out.println(roleNames);

            /*** 4.1.9****/
            /** find **/
            //List<Secrole> roleNames1 =  (List<Secrole>) template.find("from Secrole");
            List<String> rs = (List<String>) template.find("select s.roleName from Secrole s");
            System.out.println("find query::" + rs);


            String queryStr = "FROM Secrole s WHERE s.roleName = ? ";
            List<Secrole> secRoles = (List<Secrole>) template.find(queryStr, "doctor");
            List<String> roleNames = new ArrayList<>();

            for (Secrole secRole : secRoles) {
                roleNames.add(secRole.getRoleName());
            }

            for (String roleName : roleNames) {
                System.out.println("find query with parameter roleName::" + roleName);
            }

            /** findByNamedParam **/
            String q = "FROM Demographic d WHERE d.DemographicNo in (:ids)";
            List<Demographic> results = (List<Demographic>) template.findByNamedParam(q, "ids", 2);
            System.out.println("findByNamedParam ids::" + results);

            /*****/
            String[] practitionerNoTypes = null;
            List<Provider> providerList = (List<Provider>) template.findByNamedParam("From Provider p where p.practitionerNoType IN (:types) AND p.practitionerNo=:pId", new String[]{"types", "pId"}, new Object[]{practitionerNoTypes, "999998"});
            System.out.println("findByNamedParam with object providerList::" + providerList);
			            
			/*String q1 = "FROM Demographic d WHERE d.DemographicNo = ?";
			List<Demographic> providerList1 = (List<Demographic>)template.findByNamedQuery(q1, new Object[] { "1" });
			System.out.println("providerList1::"+providerList1);*/

            /** executeFind **/
            List<Demographic> demographics = getActiveDemographics(0, 1);
            System.out.println("executeFind Demographics::" + demographics);
            // List<String> roleNames1 =  (List<String>) template.execute("select s.roleName from Secrole s");


        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public List<Demographic> getActiveDemographics(final int offset, final int limit) {
        return (List<Demographic>) template.execute(new HibernateCallback<List<Demographic>>() {
            @Override
            public List<Demographic> doInHibernate(Session session) throws HibernateException {
                Query query = session.createQuery("FROM Demographic d WHERE d.PatientStatus = 'AC'");
                if (offset > 0) {
                    query.setFirstResult(offset);
                }
                int aLimit = limit;
                if (aLimit <= 0) {
                    aLimit = 500;
                }
                if (aLimit > 500) {
                    //throw new MaxSelectLimitExceededException(500, aLimit);
                    System.out.println("Error");
                }
                query.setMaxResults(aLimit);

                return query.getResultList();
            }
        });
    }
  	
  /* 	public boolean hasInde2x(){
  		String sql = "select count(*) FROM Secrole s WHERE s.roleName ='admin'";
		  Object result = template.uniqueResult(sql);
		  return result != null && ((Number) result).intValue() == 1;
  		//return  (((int)template.iterate(sql).next()) == 1);
    }*/
  	
   /* public boolean hasIndex(String index) {
        String sql = "select count(*) from Secrole p where p.roleName = ?";
        Long count = (Long) template.iterate(sql, index).next();
        return count == 1;
    }*/


}
