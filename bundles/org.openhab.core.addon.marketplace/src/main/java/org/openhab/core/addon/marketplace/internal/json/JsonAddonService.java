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
package org.openhab.core.addon.marketplace.internal.json;

import static org.openhab.core.addon.marketplace.MarketplaceConstants.*;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.addon.Addon;
import org.openhab.core.addon.AddonInfoRegistry;
import org.openhab.core.addon.AddonService;
import org.openhab.core.addon.marketplace.AbstractRemoteAddonService;
import org.openhab.core.addon.marketplace.MarketplaceAddonHandler;
import org.openhab.core.addon.marketplace.internal.json.model.AddonEntryDTO;
import org.openhab.core.config.core.ConfigParser;
import org.openhab.core.config.core.ConfigurableService;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.storage.StorageService;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.reflect.TypeToken;

/**
 * This class implements an {@link org.openhab.core.addon.AddonService} retrieving JSON marketplace information.
 *
 * @author Yannick Schaus - Initial contribution
 * @author Jan N. Klug - Refactored for JSON marketplaces
 */
@Component(immediate = true, configurationPid = JsonAddonService.SERVICE_PID, //
        property = Constants.SERVICE_PID + "=" + JsonAddonService.SERVICE_PID, service = AddonService.class)
@ConfigurableService(category = "system", label = JsonAddonService.SERVICE_NAME, description_uri = JsonAddonService.CONFIG_URI)
@NonNullByDefault
public class JsonAddonService extends AbstractRemoteAddonService {
    static final String SERVICE_NAME = "Json 3rd Party Add-on Service";
    static final String CONFIG_URI = "system:jsonaddonservice";
    static final String SERVICE_PID = "org.openhab.jsonaddonservice";

    private static final String SERVICE_ID = "json";
    private static final String ADDON_ID_PREFIX = SERVICE_ID + ":";

    private static final String CONFIG_URLS = "urls";
    private static final String CONFIG_SHOW_UNSTABLE = "showUnstable";

    private final Logger logger = LoggerFactory.getLogger(JsonAddonService.class);

    private List<String> addonServiceUrls = List.of();
    private boolean showUnstable = false;

    @Activate
    public JsonAddonService(@Reference EventPublisher eventPublisher, @Reference StorageService storageService,
            @Reference ConfigurationAdmin configurationAdmin, @Reference AddonInfoRegistry addonInfoRegistry,
            Map<String, Object> config) {
        super(eventPublisher, configurationAdmin, storageService, addonInfoRegistry, SERVICE_PID);
        modified(config);
    }

    @Modified
    public void modified(@Nullable Map<String, Object> config) {
        if (config != null) {
            String urls = ConfigParser.valueAsOrElse(config.get(CONFIG_URLS), String.class, "");
            addonServiceUrls = Arrays.asList(urls.split("\\|")).stream().filter(this::isValidUrl).toList();
            showUnstable = ConfigParser.valueAsOrElse(config.get(CONFIG_SHOW_UNSTABLE), Boolean.class, false);
            cachedRemoteAddons.invalidateValue();
            refreshSource();
        }
    }

    private boolean isValidUrl(String urlString) {
        if (urlString.isBlank()) {
            return false;
        }
        try {
            (new URI(urlString)).toURL();
        } catch (IllegalArgumentException | URISyntaxException | MalformedURLException e) {
            logger.warn("JSON Addon Service invalid URL: {}", urlString);
            return false;
        }
        return true;
    }

    @Override
    @Reference(cardinality = ReferenceCardinality.AT_LEAST_ONE, policy = ReferencePolicy.DYNAMIC)
    protected void addAddonHandler(MarketplaceAddonHandler handler) {
        this.addonHandlers.add(handler);
    }

    @Override
    protected void removeAddonHandler(MarketplaceAddonHandler handler) {
        this.addonHandlers.remove(handler);
    }

    @Override
    public String getId() {
        return SERVICE_ID;
    }

    @Override
    public String getName() {
        return SERVICE_NAME;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected List<Addon> getRemoteAddons() {
        return addonServiceUrls.stream().map(urlString -> {
            try {
                URL url = URI.create(urlString).toURL();
                URLConnection connection = url.openConnection();
                connection.addRequestProperty("Accept", "application/json");
                try (Reader reader = new InputStreamReader(connection.getInputStream())) {
                    Type type = TypeToken.getParameterized(List.class, AddonEntryDTO.class).getType();
                    return (List<AddonEntryDTO>) Objects.requireNonNull(gson.fromJson(reader, type));
                }
            } catch (IOException e) {
                return List.of();
            }
        }).flatMap(List::stream).filter(Objects::nonNull).map(e -> (AddonEntryDTO) e).filter(this::showAddon)
                .map(this::fromAddonEntry).toList();
    }

    /**
     * Check if the addon UID is present and the entry is either stable or unstable add-ons are requested
     *
     * @param addonEntry the add-on entry to check
     * @return {@code true} if the add-on entry should be processed, {@code false otherwise}
     */
    private boolean showAddon(AddonEntryDTO addonEntry) {
        if (addonEntry.uid.isBlank()) {
            logger.debug("Skipping {} because the UID is not set", addonEntry);
            return false;
        }
        if (!showUnstable && !"stable".equals(addonEntry.maturity)) {
            logger.debug("Skipping {} because the the add-on is not stable and showUnstable is disabled.", addonEntry);
            return false;
        }
        return true;
    }

    @Override
    public @Nullable Addon getAddon(String id, @Nullable Locale locale) {
        String queryId = id.startsWith(ADDON_ID_PREFIX) ? id : ADDON_ID_PREFIX + id;
        return cachedAddons.stream().filter(e -> queryId.equals(e.getUid())).findAny().orElse(null);
    }

    @Override
    public @Nullable String getAddonId(URI addonURI) {
        return null;
    }

    private Addon fromAddonEntry(AddonEntryDTO addonEntry) {
        String uid = ADDON_ID_PREFIX + addonEntry.uid;
        boolean installed = addonHandlers.stream().anyMatch(
                handler -> handler.supports(addonEntry.type, addonEntry.contentType) && handler.isInstalled(uid));

        Map<String, Object> properties = new HashMap<>();
        if (addonEntry.url.endsWith(".jar")) {
            properties.put(JAR_DOWNLOAD_URL_PROPERTY, addonEntry.url);
        } else if (addonEntry.url.endsWith(".kar")) {
            properties.put(KAR_DOWNLOAD_URL_PROPERTY, addonEntry.url);
        } else if (addonEntry.url.endsWith(".json")) {
            properties.put(JSON_DOWNLOAD_URL_PROPERTY, addonEntry.url);
        } else if (addonEntry.url.endsWith(".yaml")) {
            properties.put(YAML_DOWNLOAD_URL_PROPERTY, addonEntry.url);
        }

        boolean compatible = true;
        try {
            compatible = coreVersion.inRange(addonEntry.compatibleVersions);
        } catch (IllegalArgumentException e) {
            logger.debug("Failed to determine compatibility for addon {}: {}", addonEntry.id, e.getMessage());
        }

        return Addon.create(uid).withType(addonEntry.type).withId(addonEntry.id).withInstalled(installed)
                .withDetailedDescription(addonEntry.description).withContentType(addonEntry.contentType)
                .withAuthor(addonEntry.author).withVersion(addonEntry.version).withLabel(addonEntry.title)
                .withCompatible(compatible).withMaturity(addonEntry.maturity).withProperties(properties)
                .withLink(addonEntry.link).withImageLink(addonEntry.imageUrl)
                .withConfigDescriptionURI(addonEntry.configDescriptionURI).withLoggerPackages(addonEntry.loggerPackages)
                .withConnection(addonEntry.connection).withCountries(addonEntry.countries).build();
    }
}
