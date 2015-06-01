/*
 * The MIT License
 *
 * Copyright 2015 Oleg Nenashev.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.docker.traceability.model.jobs;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import hudson.model.listeners.ItemListener;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.docker.traceability.DockerTraceabilityPlugin;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Produces {@link DockerBuildReferenceRun} references for Docker containers and images.
 * @author Oleg Nenashev
 */
public class DockerBuildReferenceFactory {
    
    private final static Logger LOGGER = Logger.getLogger(DockerTraceabilityPlugin.class.getName());  
    private static final Object LOCK = new Object();
    
    public static @Nonnull DockerBuildReferenceRun forContainer(@Nonnull String containerId, 
            @CheckForNull String name, long timestamp) throws IOException {
        return forDockerItem(containerId, name, DockerBuildReferenceRun.Type.CONTAINER, timestamp);
    }
    
    public static @Nonnull DockerBuildReferenceRun forImage(@Nonnull String imageId, 
            @CheckForNull String name,  long timestamp) throws IOException {
        return forDockerItem(imageId, name, DockerBuildReferenceRun.Type.IMAGE, timestamp);
    }
      
    private static @Nonnull DockerBuildReferenceJob getBuildReferenceJob(@Nonnull Jenkins jenkins) throws IOException {
        TopLevelItem item = jenkins.getItem(DockerBuildReferenceJob.JOB_NAME);
        if (item instanceof DockerBuildReferenceJob) {
            return (DockerBuildReferenceJob)item;
        }
        
        if (item != null) {
            throw new IOException("Job "+DockerBuildReferenceJob.JOB_NAME+" exists, but the type is invalid");
        }
        
        // Create/reload job
        synchronized (LOCK) {
            LOGGER.fine("Loading Docker build references for fingerprints");
            return DockerBuildReferenceJob.loadJob();
        }
    }
    
    /**
     * Generates a reference {@link Run} for the Docker item (image, container, etc.).
     * @param dockerId ID of Docker container or image
     * @param name Optional name
     * @param type Type of the Docker item
     * @param timestamp Time, when the image has been created
     * @return A run for the Docker item.
     * @throws IOException File IO error
     */
    private static @Nonnull DockerBuildReferenceRun forDockerItem(@Nonnull String dockerId, 
            @CheckForNull String name, @Nonnull DockerBuildReferenceRun.Type type, long timestamp) throws IOException {
        final Jenkins j = Jenkins.getInstance();
        if (j == null) {
            throw new IllegalStateException("Jenkins instance is not ready");
        }
        DockerBuildReferenceJob refJob = getBuildReferenceJob(j);
        final DockerBuildReferenceRun run = refJob.forDockerItem(dockerId, name, type, timestamp);
        return run;
    }
    
    //TODO: remove after the fix of JENKINS-28654
    /**
     * Listens for Jenkins master node initialization and triggers 
     * {@link DockerBuildReferenceFactory} initialization.
     * @author Oleg Nenashev
     * @deprecated The class will we removed after the fix of JENKINS-28654
     */
    @Extension
    @Restricted(NoExternalUse.class)
    @Deprecated
    public static class ManageItemListener extends ItemListener {

        @Override
        public void onLoaded() {
            final Jenkins j = Jenkins.getInstance();
            if (j == null) {
                throw new IllegalStateException("Jenkins instance is not ready");
            }
            try {
                DockerBuildReferenceFactory.getBuildReferenceJob(j);
            } catch(IOException ex) {
                LOGGER.log(Level.SEVERE, "Cannot initialize the data for "+DockerBuildReferenceFactory.class, ex);
            }
        }

    }
}
