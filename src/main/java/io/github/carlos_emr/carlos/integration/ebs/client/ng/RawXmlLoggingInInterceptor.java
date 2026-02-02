package io.github.carlos_emr.carlos.integration.ebs.client.ng;

import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;

public class RawXmlLoggingInInterceptor extends AbstractPhaseInterceptor<Message> {

    public RawXmlLoggingInInterceptor() {
        // Phase.RECEIVE means run as soon as the message arrives
        super(Phase.RECEIVE);
    }

    @Override
    public void handleMessage(Message message) {
        try {
            InputStream is = message.getContent(InputStream.class);
            if (is == null) {
                return;
            }

            // Copy stream to buffer so CXF can still read it later
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }

            String xml = bos.toString("UTF-8");
            System.out.println("===== RAW INCOMING SOAP MESSAGE =====");
            System.out.println(xml);
            System.out.println("======================================");

            // Reset stream so CXF can process the message normally
            message.setContent(InputStream.class, 
                new java.io.ByteArrayInputStream(bos.toByteArray()));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}