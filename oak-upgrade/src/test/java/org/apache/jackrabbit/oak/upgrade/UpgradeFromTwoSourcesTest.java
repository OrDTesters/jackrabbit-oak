package org.apache.jackrabbit.oak.upgrade;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.core.RepositoryContext;
import org.apache.jackrabbit.core.RepositoryImpl;
import org.apache.jackrabbit.core.config.RepositoryConfig;
import org.apache.jackrabbit.oak.plugins.segment.SegmentNodeStore;
import org.apache.jackrabbit.oak.plugins.segment.file.FileStore;
import org.apache.jackrabbit.oak.spi.state.NodeStore;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.io.File;
import java.io.IOException;

/**
 * Test case that simulates copying different paths from two source repositories
 * into a single target repository.
 */
public class UpgradeFromTwoSourcesTest extends AbstractRepositoryUpgradeTest {

    private static boolean upgradeComplete;
    private static FileStore fileStore;

    @Override
    protected NodeStore createTargetNodeStore() {
        return new SegmentNodeStore(fileStore);
    }

    @BeforeClass
    public static void initialize() {
        final File dir = new File(getTestDirectory(), "segments");
        dir.mkdirs();
        try {
            fileStore = FileStore.newFileStore(dir).withMaxFileSize(128).create();
            upgradeComplete = false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterClass
    public static void cleanup() {
        fileStore.close();
        fileStore = null;
    }

    @Before
    public synchronized void upgradeRepository() throws Exception {
        if (!upgradeComplete) {
            final File sourceDir1 = new File(getTestDirectory(), "source1");
            final File sourceDir2 = new File(getTestDirectory(), "source2");

            sourceDir1.mkdirs();
            sourceDir2.mkdirs();

            final RepositoryImpl source1 = createSourceRepository(sourceDir1);
            final RepositoryImpl source2 = createSourceRepository(sourceDir2);

            try {
                createSourceContent(source1);
                createSourceContent2(source2);
            } finally {
                source1.shutdown();
                source2.shutdown();
            }

            final NodeStore target = getTargetNodeStore();
            doUpgradeRepository(sourceDir1, target, "/left");
            doUpgradeRepository(sourceDir2, target, "/right", "/left/child2", "/left/child3");
            fileStore.flush();
            upgradeComplete = true;
        }
    }

    private void doUpgradeRepository(File source, NodeStore target, String... includes) throws RepositoryException {
        final RepositoryConfig config = RepositoryConfig.create(source);
        final RepositoryContext context = RepositoryContext.create(config);
        try {
            final RepositoryUpgrade upgrade = new RepositoryUpgrade(context, target);
            upgrade.setIncludes(includes);
            upgrade.copy(null);
        } finally {
            context.getRepository().shutdown();
        }
    }

    @Override
    protected void createSourceContent(Repository repository) throws RepositoryException {
        Session session = null;
        try {
            session = repository.login(CREDENTIALS);

            JcrUtils.getOrCreateByPath("/left/child1/grandchild1", "nt:unstructured", session);
            JcrUtils.getOrCreateByPath("/left/child1/grandchild2", "nt:unstructured", session);
            JcrUtils.getOrCreateByPath("/left/child1/grandchild3", "nt:unstructured", session);
            JcrUtils.getOrCreateByPath("/left/child2/grandchild1", "nt:unstructured", session);
            JcrUtils.getOrCreateByPath("/left/child2/grandchild2", "nt:unstructured", session);

            session.save();
        } finally {
            if (session != null && session.isLive()) {
                session.logout();
            }
        }
    }

    protected void createSourceContent2(Repository repository) throws RepositoryException {
        Session session = null;
        try {
            session = repository.login(CREDENTIALS);

            JcrUtils.getOrCreateByPath("/left/child2/grandchild3", "nt:unstructured", session);
            JcrUtils.getOrCreateByPath("/left/child2/grandchild2", "nt:unstructured", session);
            JcrUtils.getOrCreateByPath("/left/child3", "nt:unstructured", session);
            JcrUtils.getOrCreateByPath("/right/child1/grandchild1", "nt:unstructured", session);
            JcrUtils.getOrCreateByPath("/right/child1/grandchild2", "nt:unstructured", session);

            session.save();
        } finally {
            if (session != null && session.isLive()) {
                session.logout();
            }
        }
    }

    @Test
    public void shouldContainNodesFromBothSources() throws Exception {
        assertExisting(
                "/",
                "/left",
                "/left/child1",
                "/left/child2",
                "/left/child3",
                "/left/child1/grandchild1",
                "/left/child1/grandchild2",
                "/left/child1/grandchild3",
                "/left/child2/grandchild2",
                "/left/child2/grandchild3",
                "/right",
                "/right/child1",
                "/right/child1/grandchild1",
                "/right/child1/grandchild2"
        );

        assertMissing(
                "/left/child2/grandchild1"
        );
    }
}