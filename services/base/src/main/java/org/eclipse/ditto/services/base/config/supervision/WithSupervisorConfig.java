/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.base.config.supervision;

/**
 * This interface provides access to the configuration settings of Actor supervision.
 */
public interface WithSupervisorConfig {

    /**
     * Returns the config of the Actor supervision.
     *
     * @return the config.
     */
    SupervisorConfig getSupervisorConfig();

}
