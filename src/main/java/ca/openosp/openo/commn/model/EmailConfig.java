//CHECKSTYLE:OFF
package ca.openosp.openo.commn.model;

import javax.persistence.*;
import java.util.List;

@Entity
@Table(name = "emailConfig")
public class EmailConfig extends AbstractModel<Integer> {

    public enum EmailType {
        SMTP,
        API
    }

    public enum EmailProvider {
        GMAIL,
        OUTLOOK,
        SENDGRID,
        LOCAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    private EmailType emailType;

    @Enumerated(EnumType.STRING)
    private EmailProvider emailProvider;

    private boolean active;

    private String senderFirstName;

    private String senderLastName;

    private String senderEmail;

    @Column(name = "configDetails", columnDefinition = "TEXT")
    private String configDetailsJson;

    @OneToMany(mappedBy = "emailConfig", fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<EmailLog> emailLogs;

    public EmailConfig() {
    }

    public EmailConfig(EmailType emailType, EmailProvider emailProvider, String senderEmail) {
        this.emailType = emailType;
        this.emailProvider = emailProvider;
        this.senderEmail = senderEmail;
    }

    @Override
    public Integer getId() {
        return id;
    }

    public EmailType getEmailType() {
        return emailType;
    }

    public void setEmailType(EmailType emailType) {
        this.emailType = emailType;
    }

    public EmailProvider getEmailProvider() {
        return emailProvider;
    }

    public void setEmailProvider(EmailProvider emailProvider) {
        this.emailProvider = emailProvider;
    }

    public boolean getActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getSenderFirstName() {
        return senderFirstName;
    }

    public void setSenderFirstName(String senderFirstName) {
        this.senderFirstName = senderFirstName;
    }

    public String getSenderLastName() {
        return senderLastName;
    }

    public void setSenderLastName(String senderLastName) {
        this.senderLastName = senderLastName;
    }

    public String getSenderFullName() {
        return senderFirstName + " " + senderLastName;
    }

    public String getSenderEmail() {
        return senderEmail;
    }

    public void setSenderEmail(String senderEmail) {
        this.senderEmail = senderEmail;
    }

    public String getConfigDetailsJson() {
        return configDetailsJson;
    }

    public void setConfigDetailsJson(String configDetailsJson) {
        this.configDetailsJson = configDetailsJson;
    }

    public List<EmailLog> getEmailLogs() {
        return emailLogs;
    }

    public void setEmailLogs(List<EmailLog> emailLogs) {
        this.emailLogs = emailLogs;
    }
}
