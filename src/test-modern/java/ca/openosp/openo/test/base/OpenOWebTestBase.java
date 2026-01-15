/**
 * Copyright (c) 2025. Magenta Health. All Rights Reserved.
 * Web/Controller Test Base Class for Struts2 Actions
 */
package ca.openosp.openo.test.base;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.opensymphony.xwork2.ActionContext;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.dispatcher.HttpParameters;

import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import org.mockito.Mock;
import org.mockito.Mockito;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for testing Struts2 Actions and web controllers.
 * Handles the complex setup needed for testing 2Actions that use
 * ServletActionContext and SpringUtils.getBean() patterns.
 */
public abstract class OpenOWebTestBase extends OpenOTestBase {

    @Autowired
    protected WebApplicationContext webApplicationContext;

    protected MockMvc mockMvc;

    protected MockHttpServletRequest mockRequest;
    protected MockHttpServletResponse mockResponse;
    protected MockHttpSession mockSession;

    @Mock
    protected SecurityInfoManager mockSecurityInfoManager;

    @Mock
    protected LoggedInInfo mockLoggedInInfo;

    // Map to store request parameters
    protected Map<String, String[]> requestParameters;

    @BeforeEach
    public void setUpWeb() {
        // Initialize MockMvc
        this.mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .build();

        // Initialize mock servlet objects
        this.mockRequest = new MockHttpServletRequest();
        this.mockResponse = new MockHttpServletResponse();
        this.mockSession = new MockHttpSession();
        this.mockRequest.setSession(mockSession);

        // Initialize request parameters
        this.requestParameters = new HashMap<>();

        // Set up Struts2 ActionContext for 2Action testing
        setUpActionContext();

        // Set up default security mocks
        setUpSecurityMocks();
    }

    /**
     * Set up Struts2 ActionContext with mock servlet objects.
     * This is critical for testing 2Actions that use ServletActionContext.
     */
    protected void setUpActionContext() {
        // Create ActionContext
        HttpParameters httpParameters = HttpParameters.create(requestParameters).build();

        // Convert MockHttpSession attributes to a Map
        Map<String, Object> sessionMap = new HashMap<>();
        var attrNames = mockSession.getAttributeNames();
        while (attrNames.hasMoreElements()) {
            String name = attrNames.nextElement();
            sessionMap.put(name, mockSession.getAttribute(name));
        }

        ActionContext context = ActionContext.of()
            .withServletRequest(mockRequest)
            .withServletResponse(mockResponse)
            .withSession(sessionMap)
            .withParameters(httpParameters);

        // Bind context to current thread
        ActionContext.bind(context);
    }

    /**
     * Set up default security mocks for testing.
     * Most 2Actions require SecurityInfoManager privilege checks.
     */
    protected void setUpSecurityMocks() {
        // Mock SecurityInfoManager to allow all privileges by default
        when(mockSecurityInfoManager.hasPrivilege(
            any(LoggedInInfo.class),
            anyString(),
            anyString(),
            any()
        )).thenReturn(true);

        // Replace the SpringUtils bean with our mock
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);

        // Set up LoggedInInfo in session with the correct key
        // LoggedInInfo uses a specific key pattern for session storage
        String loggedInInfoKey = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        mockSession.setAttribute(loggedInInfoKey, mockLoggedInInfo);
    }

    /**
     * Replace a bean in SpringUtils with a mock for testing.
     * This handles the anti-pattern where code uses SpringUtils.getBean().
     */
    protected <T> void replaceSpringUtilsBean(Class<T> beanClass, T mockBean) {
        // This is a workaround for the SpringUtils anti-pattern
        // We need to replace the bean in the application context
        try {
            // Get the bean factory
            var beanFactory = applicationContext.getAutowireCapableBeanFactory();

            // Register the mock as a singleton
            String beanName = beanClass.getSimpleName();
            beanName = Character.toLowerCase(beanName.charAt(0)) + beanName.substring(1);

            if (beanFactory instanceof org.springframework.beans.factory.support.DefaultListableBeanFactory) {
                var factory = (org.springframework.beans.factory.support.DefaultListableBeanFactory) beanFactory;
                factory.destroySingleton(beanName);
                factory.registerSingleton(beanName, mockBean);
            }
        } catch (Exception e) {
            logger.warn("Could not replace SpringUtils bean: {}", beanClass.getName(), e);
        }
    }

    /**
     * Add a request parameter for testing
     */
    protected void addRequestParameter(String name, String value) {
        requestParameters.put(name, new String[]{value});
        mockRequest.setParameter(name, value);

        // Update ActionContext parameter
        HttpParameters httpParameters = HttpParameters.create(requestParameters).build();
        ActionContext.getContext().withParameters(httpParameters);
    }

    /**
     * Add multiple request parameters
     */
    protected void addRequestParameters(Map<String, String> params) {
        params.forEach(this::addRequestParameter);
    }

    /**
     * Set session attribute
     */
    protected void setSessionAttribute(String name, Object value) {
        mockSession.setAttribute(name, value);
    }

    /**
     * Configure security to deny a specific privilege
     */
    protected void denyPrivilege(String secObject, String privilege) {
        when(mockSecurityInfoManager.hasPrivilege(
            any(LoggedInInfo.class),
            eq(secObject),
            eq(privilege),
            any()
        )).thenReturn(false);
    }

    /**
     * Configure security to allow a specific privilege
     */
    protected void allowPrivilege(String secObject, String privilege) {
        when(mockSecurityInfoManager.hasPrivilege(
            any(LoggedInInfo.class),
            eq(secObject),
            eq(privilege),
            any()
        )).thenReturn(true);
    }

    /**
     * Test a 2Action directly without going through Struts
     */
    protected String executeAction(ActionSupport action) throws Exception {
        // Ensure ActionContext is set up
        setUpActionContext();

        // Execute the action
        return action.execute();
    }

    /**
     * Test a 2Action with a specific method
     */
    protected String executeActionMethod(ActionSupport action, String methodName) throws Exception {
        // Ensure ActionContext is set up
        setUpActionContext();

        // Use reflection to call the method
        var method = action.getClass().getMethod(methodName);
        return (String) method.invoke(action);
    }

    /**
     * Verify that a 2Action properly checks security
     */
    protected void verifySecurityCheck(String secObject, String privilege) {
        Mockito.verify(mockSecurityInfoManager, Mockito.atLeastOnce())
            .hasPrivilege(
                any(LoggedInInfo.class),
                eq(secObject),
                eq(privilege),
                any()
            );
    }

    /**
     * Get the mock request for assertions
     */
    protected MockHttpServletRequest getMockRequest() {
        return mockRequest;
    }

    /**
     * Get the mock response for assertions
     */
    protected MockHttpServletResponse getMockResponse() {
        return mockResponse;
    }

    /**
     * Get the mock session for assertions
     */
    protected MockHttpSession getMockSession() {
        return mockSession;
    }

    /**
     * Clean up ActionContext after test
     */
    @Override
    protected void cleanUp() {
        ActionContext.clear();
        super.cleanUp();
    }
}