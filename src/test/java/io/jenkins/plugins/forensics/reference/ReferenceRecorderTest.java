package io.jenkins.plugins.forensics.reference;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import edu.hm.hafner.util.FilteredLog;

import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import hudson.model.Job;
import hudson.model.Run;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;

import io.jenkins.plugins.forensics.reference.ReferenceRecorder.ScmFacade;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link ReferenceRecorder}.
 *
 * @author Ullrich Hafner
 */
class ReferenceRecorderTest {
    @Test
    void shouldNotFindReferenceJobForMultiBranchProject() {
        Run<?, ?> build = mock(Run.class);
        Job<?, ?> job = createJob(build);
        createMultiBranch(job);

        ReferenceRecorder recorder = createSut();

        FilteredLog log = createLog();
        ReferenceBuild referenceBuild = recorder.findReferenceBuild(build, log);

        assertThat(log.getInfoMessages())
                .anySatisfy(m -> assertThat(m).contains("Found a `MultiBranchProject`"))
                .anySatisfy(m -> assertThat(m).contains("falling back to plugin default target branch 'master'"));

        assertThat(referenceBuild.getReferenceBuild()).isEmpty();
    }

    @Test
    void shouldObtainReferenceFromPullRequestTarget() {
        Run<?, ?> build = mock(Run.class);
        Job<?, ?> job = createJob(build);
        WorkflowMultiBranchProject parent = createMultiBranch(job);

        ReferenceRecorder recorder = createSut();

        Run<?, ?> prBuild = configurePrJobAndBuild(recorder, parent, job);
        when(recorder.find(build, prBuild)).thenReturn(Optional.of(prBuild));

        FilteredLog log = createLog();
        ReferenceBuild referenceBuild = recorder.findReferenceBuild(build, log);

        assertThat(log.getInfoMessages())
                .anySatisfy(m -> assertThat(m).contains("no target branch configured in step"))
                .anySatisfy(m -> assertThat(m).contains("detected a pull or merge request 'pr' for target branch 'pr-target'"));

        assertThat(referenceBuild.getReferenceBuildId()).isEqualTo("pr-id");
    }

    @Test
    void targetShouldHavePrecedenceBeforePullRequestTarget() {
        Run<?, ?> build = mock(Run.class);
        Job<?, ?> job = createJob(build);
        WorkflowMultiBranchProject parent = createMultiBranch(job);

        ReferenceRecorder recorder = createSut();
        Run<?, ?> targetBuild = configureTargetJobAndBuild(recorder, parent);

        configurePrJobAndBuild(recorder, parent, job); // will not be used since target branch has been set

        when(recorder.find(build, targetBuild)).thenReturn(Optional.of(targetBuild));

        FilteredLog log = createLog();
        ReferenceBuild referenceBuild = recorder.findReferenceBuild(build, log);

        assertThat(log.getInfoMessages())
                .anySatisfy(m -> assertThat(m).contains("using target branch 'target' as configured in step"))
                .noneSatisfy(m-> assertThat(m).contains("detected a pull or merge request"));

        assertThat(referenceBuild.getReferenceBuildId()).isEqualTo("target-id");
    }

    private ReferenceRecorder createSut() {
        return mock(ReferenceRecorder.class, CALLS_REAL_METHODS);
    }

    private FilteredLog createLog() {
        return new FilteredLog("EMPTY");
    }

    private WorkflowMultiBranchProject createMultiBranch(final Job<?, ?> job) {
        WorkflowMultiBranchProject parent = mock(WorkflowMultiBranchProject.class);
        when(job.getParent()).thenAnswer(i -> parent);
        return parent;
    }

    private Job<?, ?> createJob(final Run<?, ?> build) {
        Job<?, ?> job = mock(Job.class);
        when(build.getParent()).thenAnswer(i -> job);
        return job;
    }

    private Run<?, ?> configurePrJobAndBuild(final ReferenceRecorder recorder,
            final WorkflowMultiBranchProject parent, final Job<?, ?> job) {
        Job<?, ?> prJob = mock(Job.class);
        when(parent.getItemByBranchName("pr-target")).thenAnswer(i-> prJob);
        Run<?, ?> prBuild = mock(Run.class);
        when(prBuild.getExternalizableId()).thenReturn("pr-id");
        when(prJob.getLastCompletedBuild()).thenAnswer(i -> prBuild);

        ScmFacade scmFacade = mock(ScmFacade.class);
        SCMHead pr = mock(SCMHead.class, withSettings().extraInterfaces(ChangeRequestSCMHead.class));
        when(pr.toString()).thenReturn("pr");
        when(scmFacade.findHead(job)).thenAnswer(i -> pr);
        SCMHead target = mock(SCMHead.class);
        when(((ChangeRequestSCMHead) pr).getTarget()).thenReturn(target);
        when(target.getName()).thenReturn("pr-target");

        when(recorder.getScmFacade()).thenReturn(scmFacade);

        return prBuild;
    }

    private Run<?, ?> configureTargetJobAndBuild(final ReferenceRecorder recorder,
            final WorkflowMultiBranchProject parent) {
        recorder.setTargetBranch("target");

        Job<?, ?> targetJob = mock(Job.class);
        when(parent.getItemByBranchName("target")).thenAnswer(i-> targetJob);
        Run<?, ?> targetBuild = mock(Run.class);
        when(targetBuild.getExternalizableId()).thenReturn("target-id");
        when(targetJob.getLastCompletedBuild()).thenAnswer(i -> targetBuild);

        return targetBuild;
    }
}
