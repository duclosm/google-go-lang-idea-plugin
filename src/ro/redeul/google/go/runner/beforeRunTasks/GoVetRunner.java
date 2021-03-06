package ro.redeul.google.go.runner.beforeRunTasks;

import com.intellij.execution.CantRunException;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ro.redeul.google.go.config.sdk.GoSdkData;
import ro.redeul.google.go.ide.ui.GoToolWindow;
import ro.redeul.google.go.runner.GoApplicationConfiguration;
import ro.redeul.google.go.runner.GoCommonConsoleView;
import ro.redeul.google.go.sdk.GoSdkUtil;

import java.io.File;
import java.util.Map;

/**
 * Created by d3xter on 13.03.14.
 */
public class GoVetRunner extends Task.Backgroundable {
    private static final Logger LOG = Logger.getInstance(GoVetRunner.class);
    private GoApplicationConfiguration goConfig;

    public GoVetRunner(@Nullable Project project, @NotNull String title, boolean canBeCancelled, GoApplicationConfiguration goConfig) {
        super(project, title, canBeCancelled);

        this.goConfig = goConfig;
    }

    public GoVetRunner(@Nullable Project project, @NotNull String title, GoApplicationConfiguration goConfig) {
        this(project, title, false, goConfig);
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
        LOG.assertTrue(!ApplicationManager.getApplication().isReadAccessAllowed());

        GoToolWindow toolWindow = GoToolWindow.getInstance(myProject);

        indicator.setText(this.myTitle);
        indicator.setFraction(0);

        Sdk sdk = GoSdkUtil.getGoogleGoSdkForProject(myProject);
        if ( sdk == null ) {
            LOG.error("No Go Sdk defined for this project");
        }

        final GoSdkData sdkData = (GoSdkData)sdk.getSdkAdditionalData();
        if ( sdkData == null ) {
            LOG.error("No Go Sdk defined for this project");
        }

        String goExecName = sdkData.GO_EXEC;
        String projectDir = myProject.getBasePath();

        try {
            Map<String,String> sysEnv = GoSdkUtil.getExtendedSysEnv(sdkData, projectDir, goConfig.envVars);
            String[] goEnv = GoSdkUtil.convertEnvMapToArray(sysEnv);

            String command = String.format(
                    "%s vet ./...%n",
                    goExecName
            );

            Runtime rt = Runtime.getRuntime();
            Process proc = rt.exec(command, goEnv, new File(projectDir));
            OSProcessHandler handler = new OSProcessHandler(proc, null);
            toolWindow.attachConsoleViewToProcess(handler);
            toolWindow.printNormalMessage(String.format("%s%n", command));
            handler.startNotify();

            if (proc.waitFor() == 0) {
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        VirtualFileManager.getInstance().syncRefresh();
                    }
                });
                toolWindow.printNormalMessage(String.format("%nFinished running go vet on project %s%n", projectDir));
            } else {
                toolWindow.printErrorMessage(String.format("%nCouldn't vet project %s%n", projectDir));
            }
        } catch (Exception e) {
            toolWindow.printErrorMessage(String.format("Error while processing %s vet command.%n", goExecName));

            //An Exception shouldn't happen, so print a log-error
            LOG.error(String.format("Error while processing %s vet command.%n", goExecName));
        }
        finally {
            indicator.setFraction(100);
        }
    }
}
