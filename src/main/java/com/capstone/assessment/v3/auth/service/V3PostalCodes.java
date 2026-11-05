package com.capstone.assessment.v3.auth.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * R2-3 (REG-4): a postal code must be a Philippine ZIP code and belong to the selected
 * city/municipality. ZIP codes belong to a city or municipality (Metro Manila: its districts),
 * not to a barangay, so the barangay cannot be checked.
 * Data: v3/ph-postal-codes.csv (GeoNames postal codes, CC BY 4.0, matched to PSA PSGC codes).
 * A ZIP that is known but whose city could not be matched is accepted for any city.
 */
public final class V3PostalCodes {

    private static final String RESOURCE = "v3/ph-postal-codes.csv";
    private static final int LISTED_ZIPS = 6;
    private static final Directory DIRECTORY = load();

    private V3PostalCodes() {
    }

    private record Directory(
            Set<String> zips,
            Map<String, Set<String>> zipsByCity,
            Map<String, Set<String>> citiesByZip,
            Map<String, String> cityNames
    ) {
    }

    /** Why the postal code does not fit, or empty when it is accepted. */
    public static Optional<String> problem(String postalCode, String cityMunicipalityCode, String cityMunicipalityName) {
        String zip = postalCode == null ? "" : postalCode.trim();
        if (!zip.matches("\\d{4}")) {
            return Optional.of("Postal code must be 4 digits.");
        }
        if (!DIRECTORY.zips().contains(zip)) {
            return Optional.of("ZIP code " + zip + " is not a Philippine ZIP code.");
        }
        String city = cityMunicipalityCode == null ? "" : cityMunicipalityCode.trim();
        Set<String> cityZips = DIRECTORY.zipsByCity().get(city);
        if (cityZips == null || cityZips.contains(zip)) {
            return Optional.empty();
        }
        Set<String> owners = DIRECTORY.citiesByZip().getOrDefault(zip, Set.of());
        if (owners.isEmpty()) {
            return Optional.empty();
        }
        String place = cityMunicipalityName == null || cityMunicipalityName.isBlank()
                ? DIRECTORY.cityNames().getOrDefault(city, "the selected city or municipality")
                : cityMunicipalityName.trim();
        String owner = owners.stream()
                .map(code -> DIRECTORY.cityNames().getOrDefault(code, code))
                .sorted()
                .collect(Collectors.joining(" / "));
        String valid = cityZips.stream().sorted().limit(LISTED_ZIPS).collect(Collectors.joining(", "));
        return Optional.of("ZIP code " + zip + " belongs to " + owner + ", not " + place + ". ZIP code(s) for "
                + place + ": " + valid + (cityZips.size() > LISTED_ZIPS ? ", ..." : "") + ".");
    }

    private static Directory load() {
        Set<String> zips = new HashSet<>();
        Map<String, Set<String>> zipsByCity = new HashMap<>();
        Map<String, Set<String>> citiesByZip = new HashMap<>();
        Map<String, String> cityNames = new HashMap<>();
        try (InputStream input = V3PostalCodes.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing " + RESOURCE);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#") || line.startsWith("zip,")) {
                    continue;
                }
                String[] parts = line.split(",", -1);
                String zip = parts[0].trim();
                zips.add(zip);
                String city = parts.length > 1 ? parts[1].trim() : "";
                if (!city.isEmpty()) {
                    zipsByCity.computeIfAbsent(city, key -> new TreeSet<>()).add(zip);
                    citiesByZip.computeIfAbsent(zip, key -> new TreeSet<>()).add(city);
                    if (parts.length > 2 && !parts[2].isBlank()) {
                        cityNames.put(city, parts[2].trim());
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + RESOURCE, exception);
        }
        return new Directory(Collections.unmodifiableSet(zips), zipsByCity, citiesByZip, cityNames);
    }
}
