// (C) Copyright 2003-2015 Hewlett-Packard Development Company, L.P.

package com.hp.octane.plugins.jenkins.configuration;

import com.google.inject.Inject;
import com.hp.mqm.client.MqmRestClient;
import com.hp.mqm.client.exception.AuthenticationException;
import com.hp.mqm.client.exception.SharedSpaceNotExistException;
import com.hp.mqm.client.exception.RequestErrorException;
import com.hp.mqm.client.exception.SessionCreationException;
import com.hp.octane.plugins.jenkins.Messages;
import com.hp.octane.plugins.jenkins.OctanePlugin;
import com.hp.octane.plugins.jenkins.client.JenkinsMqmRestClientFactory;
import com.hp.octane.plugins.jenkins.client.JenkinsMqmRestClientFactoryImpl;
import hudson.Extension;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class ConfigurationService {

    private final static Logger logger = Logger.getLogger(ConfigurationService.class.getName());

    private static final String PARAM_DOMAIN_PROJECT = "p"; // NON-NLS

    private JenkinsMqmRestClientFactory clientFactory;

    public static ServerConfiguration getServerConfiguration() {
        OctanePlugin octanePlugin = Jenkins.getInstance().getPlugin(OctanePlugin.class);
        return octanePlugin.getServerConfiguration();
    }

    public static MqmProject parseUiLocation(String uiLocation) throws FormValidation {
        try {
            URL url = new URL(uiLocation);
            String location;
            int contextPos = uiLocation.indexOf("/qcbin/ui");
            if (contextPos < 0) {
                // guessing the future
                contextPos = uiLocation.indexOf("/mqm/ui");
                if (contextPos < 0) {
                    throw FormValidation.errorWithMarkup(markup("red", Messages.ApplicationContextNotFound()));
                } else {
                    location = uiLocation.substring(0, contextPos + 4);
                }
            } else {
                location = uiLocation.substring(0, contextPos + 6);
            }
            List<NameValuePair> params = URLEncodedUtils.parse(url.toURI(), "UTF-8");
            for (NameValuePair param: params) {
                if (param.getName().equals(PARAM_DOMAIN_PROJECT)) {
                    String sharedSpace = param.getValue();
                    if (StringUtils.isEmpty(sharedSpace)) {
                        throw FormValidation.errorWithMarkup(markup("red", Messages.UnexpectedSharedSpace()));
                    }
                    return new MqmProject(location, sharedSpace);
                }
            }
            throw FormValidation.errorWithMarkup(markup("red", Messages.MissingDomainProject()));
        } catch (MalformedURLException e) {
            throw FormValidation.errorWithMarkup(markup("red", Messages.ConfigurationUrInvalid()));
        } catch (URISyntaxException e) {
            throw FormValidation.errorWithMarkup(markup("red", Messages.ConfigurationUrInvalid()));
        }
    }

    public FormValidation checkConfiguration(String location, String sharedSpace, String username, String password) {
        MqmRestClient client = clientFactory.create(location, sharedSpace, username, password);
        try {
            client.tryToConnectProject();
        } catch (AuthenticationException e) {
            logger.log(Level.WARNING, "Authentication failed.", e);
            return FormValidation.errorWithMarkup(markup("red", Messages.AuthenticationFailure()));
        } catch (SessionCreationException e) {
            logger.log(Level.WARNING, "Session creation failed.", e);
            return FormValidation.errorWithMarkup(markup("red", Messages.SessionCreationFailure()));
        } catch (SharedSpaceNotExistException e) {
            logger.log(Level.WARNING, "Shared Space validation failed.", e);
            return FormValidation.errorWithMarkup(markup("red", Messages.ConnectionSharedSpaceInvalid()));
        } catch (RequestErrorException e) {
            logger.log(Level.WARNING, "Connection check failed due to communication problem.", e);
            return FormValidation.errorWithMarkup(markup("red", Messages.ConnectionFailure()));
        }
        return FormValidation.okWithMarkup(markup("green", Messages.ConnectionSuccess()));
    }

    private static String markup(String color, String message) {
        return "<font color=\"" + color + "\"><b>" + message + "</b></font>";
    }

    @Inject
    public void setMqmRestClientFactory(JenkinsMqmRestClientFactoryImpl clientFactory) {
        this.clientFactory = clientFactory;
    }

    /*
     * To be used in tests only.
     */
    public void _setMqmRestClientFactory(JenkinsMqmRestClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }
}
