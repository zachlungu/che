/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.api.workspace.server.jpa;

import com.google.inject.persist.Transactional;

import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.jdbc.jpa.DuplicateKeyException;
import org.eclipse.che.api.workspace.server.model.impl.ProjectConfigImpl;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceImpl;
import org.eclipse.che.api.workspace.server.spi.WorkspaceDao;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import java.util.List;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * JPA based implementation of {@link WorkspaceDao}.
 *
 * @author Yevhenii Voevodin
 */
@Singleton
public class JpaWorkspaceDao implements WorkspaceDao {

    @Inject
    private Provider<EntityManager> manager;

    @Override
    public WorkspaceImpl create(WorkspaceImpl workspace) throws ConflictException, ServerException {
        requireNonNull(workspace, "Required non-null workspace");
        try {
            doCreate(workspace);
        } catch (DuplicateKeyException dkEx) {
            throw new ConflictException(format("Workspace with id '%s' or name '%s' in namespace '%s' already exists",
                                               workspace.getId(),
                                               workspace.getNamespace(),
                                               workspace.getName()));
        } catch (RuntimeException x) {
            throw new ServerException(x.getMessage(), x);
        }
        return workspace;
    }

    @Override
    public WorkspaceImpl update(WorkspaceImpl update) throws NotFoundException, ConflictException, ServerException {
        requireNonNull(update, "Required non-null update");
        try {
            // It is impossible to synchronize attributes in @PostPersist
            // because merge won't make current object managed one, that's
            // why synchronization is performed directly before update
            update.getConfig().getProjects().forEach(ProjectConfigImpl::syncDbAttributes);
            return doUpdate(update);
        } catch (DuplicateKeyException dkEx) {
            throw new ConflictException(format("Workspace with name '%s' in namespace '%s' already exists",
                                               update.getNamespace(),
                                               update.getName()));
        } catch (RuntimeException x) {
            throw new ServerException(x.getMessage(), x);
        }
    }

    @Override
    public void remove(String id) throws ConflictException, ServerException {
        requireNonNull(id, "Required non-null id");
        try {
            doRemove(id);
        } catch (RuntimeException x) {
            throw new ServerException(x.getLocalizedMessage(), x);
        }
    }

    @Override
    @Transactional
    public WorkspaceImpl get(String id) throws NotFoundException, ServerException {
        requireNonNull(id, "Required non-null id");
        try {
            final WorkspaceImpl workspace = manager.get().find(WorkspaceImpl.class, id);
            if (workspace == null) {
                throw new NotFoundException(format("Workspace with id '%s' doesn't exist", id));
            }
            return workspace;
        } catch (RuntimeException x) {
            throw new ServerException(x.getLocalizedMessage(), x);
        }
    }

    @Override
    @Transactional
    public WorkspaceImpl get(String name, String namespace) throws NotFoundException, ServerException {
        requireNonNull(name, "Required non-null name");
        requireNonNull(namespace, "Required non-null namespace");
        try {
            return manager.get()
                          .createNamedQuery("Workspace.getByName", WorkspaceImpl.class)
                          .setParameter("namespace", namespace)
                          .setParameter("name", name)
                          .getSingleResult();
        } catch (NoResultException noResEx) {
            throw new NotFoundException(format("Workspace with name '%s' in namespace '%s' doesn't exist",
                                               name,
                                               namespace));
        } catch (RuntimeException x) {
            throw new ServerException(x.getLocalizedMessage(), x);
        }
    }

    @Override
    @Transactional
    public List<WorkspaceImpl> getByNamespace(String namespace) throws ServerException {
        requireNonNull(namespace, "Required non-null namespace");
        try {
            return manager.get()
                          .createNamedQuery("Workspace.getByNamespace", WorkspaceImpl.class)
                          .setParameter("namespace", namespace)
                          .getResultList();
        } catch (RuntimeException x) {
            throw new ServerException(x.getLocalizedMessage(), x);
        }
    }

    @Override
    @Transactional
    public List<WorkspaceImpl> getWorkspaces(String userId) throws ServerException {
        // TODO respect userId when workers become a part of che
        try {
            return manager.get().createNamedQuery("Workspace.getAll", WorkspaceImpl.class).getResultList();
        } catch (RuntimeException x) {
            throw new ServerException(x.getLocalizedMessage(), x);
        }
    }

    @Transactional
    protected void doCreate(WorkspaceImpl workspace) {
        manager.get().persist(workspace);
    }

    @Transactional
    protected void doRemove(String id) {
        final WorkspaceImpl workspace = manager.get().find(WorkspaceImpl.class, id);
        if (workspace != null) {
            manager.get().remove(workspace);
        }
    }

    @Transactional
    protected WorkspaceImpl doUpdate(WorkspaceImpl update) throws NotFoundException {
        if (manager.get().find(WorkspaceImpl.class, update.getId()) == null) {
            throw new NotFoundException(format("Workspace with id '%s' doesn't exist", update.getId()));
        }
        return manager.get().merge(update);
    }
}
