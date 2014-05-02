/*
 * bean-cp
 * Copyright (c) 2014, Rafal Chojnacki, All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */
package org.beancp;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import static org.beancp.ConstraintUtils.failIfNull;

/**
 * Builds mapper implementation. This class do not guarantee to be thread-safe.
 */
public final class MapperBuilder implements MappingsInfo {

    private final List<MapExecutor<?, ?>> mapExecutors = new LinkedList<>();

    private final List<MappingConvention> mapAnyConvention = new LinkedList<>();

    private boolean mapperBuilded = false;

    /**
     * Adds new mapping defined by map. Both {@code source} and {@code destination} classes must:
     * <ul>
     * <li>Must have default public constructor or have not been final and have default protected
     * constructor. This requirement is valid even if destination object builder is provided by
     * {@link Map#constructDestinationObjectUsing(java.util.function.Supplier)} method.</li>
     * <li>Cannot be inner non-static classes.</li>
     * </ul>
     *
     * @param <S> source object class.
     * @param <D> destination object class.
     * @param sourceClass source object class.
     * @param destinationClass destination object class.
     * @param mapConfiguration map configuration.
     *
     * @return this (for method chaining)
     */
    public <S, D> MapperBuilder addMap(final Class<S> sourceClass, final Class<D> destinationClass,
            final MapSetup<S, D> mapConfiguration) throws MapperConfigurationException {
        validateAddMappingAction(sourceClass, destinationClass);

        MapImpl map = new MapImpl(sourceClass, destinationClass, mapConfiguration);
        map.configure(this);

        mapExecutors.add(map);

        return this;
    }

    /**
     * Adds new mapping implemented by converter.
     *
     * @param <S> source object class.
     * @param <D> destination object class.
     * @param sourceClass source object class.
     * @param destinationClass destination object class.
     * @param convertionAction converter action, must be thread-safe.
     *
     * @return this (for method chaining)
     */
    public <S, D> MapperBuilder addConverter(final Class<S> sourceClass,
            final Class<D> destinationClass,
            final BiConsumer<S, D> convertionAction) throws MapperConfigurationException {
        validateAddMappingAction(sourceClass, destinationClass);

        TriConsumer<Mapper, S, D> convertActionWrapper
                = (Mapper mapper, S source, D destination)
                -> convertionAction.accept(source, destination);

        addConverter(sourceClass, destinationClass, convertActionWrapper);

        return this;
    }

    /**
     * Adds new mapping implemented by converter.
     *
     * @param <S> source object class.
     * @param <D> destination object class.
     * @param sourceClass source object class.
     * @param destinationClass destination object class.
     * @param convertionAction converter action, must be thread-safe.
     * @param destinationObjectBuilder destination object builder.
     *
     * @return this (for method chaining)
     */
    public <S, D> MapperBuilder addConverter(final Class<S> sourceClass,
            final Class<D> destinationClass,
            final BiConsumer<S, D> convertionAction,
            final Supplier<D> destinationObjectBuilder) throws MapperConfigurationException {
        validateAddMappingAction(sourceClass, destinationClass);

        TriConsumer<Mapper, S, D> convertActionWrapper
                = (Mapper mapper, S source, D destination)
                -> convertionAction.accept(source, destination);

        addConverter(sourceClass, destinationClass, convertActionWrapper, destinationObjectBuilder);

        return this;
    }

    /**
     * Adds new mapping implemented by converter.
     *
     * @param <S> source object class.
     * @param <D> destination object class.
     * @param sourceClass source object class.
     * @param destinationClass destination object class.
     * @param convertionAction converter action, must be thread-safe.
     *
     * @return this (for method chaining)
     */
    public <S, D> MapperBuilder addConverter(final Class<S> sourceClass,
            final Class<D> destinationClass,
            final TriConsumer<Mapper, S, D> convertionAction) throws MapperConfigurationException {
        validateAddMappingAction(sourceClass, destinationClass);

        addConverter(sourceClass, destinationClass, convertionAction, null);

        return this;
    }

    /**
     * Adds new mapping implemented by converter.
     *
     * @param <S> source object class.
     * @param <D> destination object class.
     * @param sourceClass source object class.
     * @param destinationClass destination object class.
     * @param convertionAction converter action, must be thread-safe.
     * @param destinationObjectBuilder destination object builder.
     *
     * @return this (for method chaining)
     */
    public <S, D> MapperBuilder addConverter(final Class<S> sourceClass,
            final Class<D> destinationClass,
            final TriConsumer<Mapper, S, D> convertionAction,
            final Supplier<D> destinationObjectBuilder) throws MapperConfigurationException {
        validateAddMappingAction(sourceClass, destinationClass);

        Converter converter = new Converter(
                sourceClass, destinationClass, convertionAction, destinationObjectBuilder);

        mapExecutors.add(converter);

        return this;
    }

    /**
     * If two data types has no mapping defined by
     * {@link #addMap(java.lang.Class, java.lang.Class, org.beancp.MapSetup)} or any of
     * {@code addConverter} methods then this convention will be used.
     *
     * @param conventions convention to add.
     *
     * @return this (for method chaining)
     */
    public MapperBuilder addMapAnyByConvention(final MappingConvention... conventions)
            throws MapperConfigurationException {
        this.mapAnyConvention.addAll(Arrays.asList(conventions));

        return this;
    }

    /**
     * Creates map implementation from definitions. After executed no other methods can be executed
     * on this instance.
     *
     * @return map implementation.
     */
    public Mapper buildMapper() {
        this.mapperBuilded = true;

        return new MapperImpl(mapExecutors, mapAnyConvention);
    }

    @Override
    public boolean isMapperAvailable(final Class sourceClass, final Class destinationClass) {
        return MapperSelector.isMappingAvailable(
                this,
                sourceClass,
                destinationClass,
                Collections.unmodifiableCollection(mapExecutors),
                Collections.unmodifiableCollection(mapAnyConvention));
    }

    private <S, D> void validateAddMappingAction(final Class<S> sourceClass,
            final Class<D> destinationClass) {
        failIfNull(sourceClass, "sourceClass");
        failIfNull(destinationClass, "destinationClass");

        if (this.mapperBuilded) {
            throw new MapperConfigurationException("Mapper already builded. No changes allowed.");
        }

        for (MapExecutor<?, ?> i : mapExecutors) {
            if (i.getSourceClass().equals(sourceClass)
                    && i.getDestinationClass().equals(destinationClass)) {
                throw new MapperConfigurationException(String.format(
                        "Mapping from %s to %s already defined.",
                        sourceClass.getName(), destinationClass.getName()));
            }
        }
    }
}
