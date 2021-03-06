package hudson.plugins.jira.versionparameter;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.plugins.jira.JiraSession;
import hudson.plugins.jira.JiraSite;
import hudson.plugins.jira.JiraVersion;
import hudson.plugins.jira.soap.RemoteVersion;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import javax.xml.rpc.ServiceException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static hudson.Util.fixNull;
import static java.util.Arrays.asList;

public class JiraVersionParameterDefinition extends ParameterDefinition {
    private static final long serialVersionUID = 3927562542249244416L;

    private String projectKey;
    private boolean showReleased = false;
    private boolean showArchived = false;
    private Pattern pattern = null;

    @DataBoundConstructor
    public JiraVersionParameterDefinition(String name, String description, String jiraProjectKey, String jiraReleasePattern, String jiraShowReleased, String jiraShowArchived) {
        super(name, description);
        setJiraProjectKey(jiraProjectKey);
        setJiraReleasePattern(jiraReleasePattern);
        setJiraShowReleased(jiraShowReleased);
        setJiraShowArchived(jiraShowArchived);
    }

    @Override
    public ParameterValue createValue(StaplerRequest req) {
        String[] values = req.getParameterValues(getName());
        if (values == null || values.length != 1) {
            return null;
        }
        return new JiraVersionParameterValue(getName(), values[0]);
    }


    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject formData) {
        JiraVersionParameterValue value = req.bindJSON(JiraVersionParameterValue.class, formData);
        return value;
    }

    public List<JiraVersionParameterDefinition.Result> getVersions() throws IOException, ServiceException {
        AbstractProject<?, ?> context = Stapler.getCurrentRequest().findAncestorObject(AbstractProject.class);

        JiraSite site = JiraSite.get(context);
        if (site == null)
            throw new IllegalStateException("JIRA site needs to be configured in the project " + context.getFullDisplayName());

        JiraSession session = site.createSession();
        if (session == null) throw new IllegalStateException("Remote SOAP access for JIRA isn't configured in Jenkins");

        RemoteVersion[] versions = session.getVersions(projectKey);
        SortedSet<RemoteVersion> orderVersions = new TreeSet<RemoteVersion>(new CompRemoteVersion());
        orderVersions.addAll(fixNull(asList(versions)));
        
        List<Result> projectVersions = new ArrayList<Result>();

        for (RemoteVersion version : orderVersions) {
            if (match(version)) projectVersions.add(new Result(version));
        }

        return projectVersions;
    }

    private boolean match(RemoteVersion version) {
        // Match regex if it exists
        if (pattern != null) {
            if (!pattern.matcher(version.getName()).matches()) return false;
        }

        // Filter released versions
        if (!showReleased && version.isReleased()) return false;

        // Filter archived versions
        if (!showArchived && version.isArchived()) return false;

        return true;
    }

    public String getJiraReleasePattern() {
        if (pattern == null) return "";
        return pattern.pattern();
    }

    public void setJiraReleasePattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            this.pattern = null;
        } else {
            this.pattern = Pattern.compile(pattern);
        }
    }

    public String getJiraProjectKey() {
        return projectKey;
    }

    public void setJiraProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getJiraShowReleased() {
        return Boolean.toString(showReleased);
    }

    public void setJiraShowReleased(String showReleased) {
        this.showReleased = Boolean.parseBoolean(showReleased);
    }


    public String getJiraShowArchived() {
        return Boolean.toString(showArchived);
    }

    public void setJiraShowArchived(String showArchived) {
        this.showArchived = Boolean.parseBoolean(showArchived);
    }


    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {
        @Override
        public String getDisplayName() {
            return "JIRA Release Version Parameter";
        }
    }

    public static class Result {
        public final String name;
        public final String id;

        public Result(final RemoteVersion version) {
            this.name = version.getName();
            this.id = version.getId();
        }
    }
}
