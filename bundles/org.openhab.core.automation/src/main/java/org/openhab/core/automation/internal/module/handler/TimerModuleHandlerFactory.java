/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.core.automation.internal.module.handler;

import java.util.Arrays;
import java.util.Collection;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.automation.Condition;
import org.openhab.core.automation.Module;
import org.openhab.core.automation.Trigger;
import org.openhab.core.automation.handler.BaseModuleHandlerFactory;
import org.openhab.core.automation.handler.ModuleHandler;
import org.openhab.core.automation.handler.ModuleHandlerFactory;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.scheduler.CronScheduler;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This HandlerFactory creates TimerTriggerHandlers to control items within the
 * RuleManager.
 *
 * @author Christoph Knauf - Initial contribution
 * @author Kai Kreuzer - added new module types
 */
@NonNullByDefault
@Component(immediate = true, service = ModuleHandlerFactory.class)
public class TimerModuleHandlerFactory extends BaseModuleHandlerFactory {

    private final Logger logger = LoggerFactory.getLogger(TimerModuleHandlerFactory.class);

    public static final String THREADPOOLNAME = "ruletimer";
    private static final Collection<String> TYPES = Arrays.asList(GenericCronTriggerHandler.MODULE_TYPE_ID,
            TimeOfDayTriggerHandler.MODULE_TYPE_ID, TimeOfDayConditionHandler.MODULE_TYPE_ID,
            DayOfWeekConditionHandler.MODULE_TYPE_ID, DateTimeTriggerHandler.MODULE_TYPE_ID,
            IntervalConditionHandler.MODULE_TYPE_ID);

    private final CronScheduler scheduler;
    private final ItemRegistry itemRegistry;
    private final BundleContext bundleContext;

    @Activate
    public TimerModuleHandlerFactory(final @Reference CronScheduler scheduler,
            final @Reference ItemRegistry itemRegistry, final BundleContext bundleContext) {
        this.scheduler = scheduler;
        this.itemRegistry = itemRegistry;
        this.bundleContext = bundleContext;
    }

    @Override
    @Deactivate
    public void deactivate() {
        super.deactivate();
    }

    @Override
    public Collection<String> getTypes() {
        return TYPES;
    }

    @Override
    protected @Nullable ModuleHandler internalCreate(Module module, String ruleUID) {
        logger.trace("create {} -> {}", module.getId(), module.getTypeUID());
        String moduleTypeUID = module.getTypeUID();
        if (module instanceof Trigger trigger) {
            switch (moduleTypeUID) {
                case GenericCronTriggerHandler.MODULE_TYPE_ID:
                    return new GenericCronTriggerHandler(trigger, scheduler);
                case TimeOfDayTriggerHandler.MODULE_TYPE_ID:
                    return new TimeOfDayTriggerHandler(trigger, scheduler);
                case DateTimeTriggerHandler.MODULE_TYPE_ID:
                    return new DateTimeTriggerHandler(trigger, scheduler, itemRegistry, bundleContext);
            }
        } else if (module instanceof Condition condition) {
            switch (moduleTypeUID) {
                case TimeOfDayConditionHandler.MODULE_TYPE_ID:
                    return new TimeOfDayConditionHandler(condition);
                case DayOfWeekConditionHandler.MODULE_TYPE_ID:
                    return new DayOfWeekConditionHandler(condition);
                case IntervalConditionHandler.MODULE_TYPE_ID:
                    return new IntervalConditionHandler(condition);
            }
        }
        logger.error("The module handler type '{}' is not supported.", moduleTypeUID);
        return null;
    }
}
