/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util.portable;

import org.gridgain.grid.kernal.*;
import org.gridgain.grid.kernal.processors.rest.client.message.*;
import org.gridgain.grid.util.typedef.internal.*;
import org.gridgain.portable.*;
import org.jdk8.backport.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import static org.gridgain.grid.util.portable.GridPortableMarshaller.*;

/**
 * Portable configurer.
 */
public class GridPortableContextImpl implements GridPortableContext, Externalizable {
    /** */
    private final ConcurrentMap<Class<?>, GridPortableClassDescriptor> descByCls = new ConcurrentHashMap8<>();

    /** */
    private final Map<DescriptorKey, GridPortableClassDescriptor> descById = new HashMap<>();

    /** */
    private final Map<Class<? extends Collection>, Byte> colTypes = new HashMap<>();

    /** */
    private final Map<Class<? extends Map>, Byte> mapTypes = new HashMap<>();

    /** */
    private final Map<Integer, GridPortableIdMapper> mappers = new HashMap<>();

    /** */
    private String gridName;

    /**
     * For {@link Externalizable}.
     */
    public GridPortableContextImpl() {
        // No-op.
    }

    /**
     * @param gridName Grid name.
     */
    public GridPortableContextImpl(@Nullable String gridName) {
        this.gridName = gridName;
    }

    /**
     * @param portableCfg Portable configuration.
     * @return Portable context.
     * @throws GridPortableException In case of error.
     */
    public void configure(@Nullable GridPortableConfiguration portableCfg)
        throws GridPortableException {
        addDescriptor(Byte.class, BYTE);
        addDescriptor(Short.class, SHORT);
        addDescriptor(Integer.class, INT);
        addDescriptor(Long.class, LONG);
        addDescriptor(Float.class, FLOAT);
        addDescriptor(Double.class, DOUBLE);
        addDescriptor(Character.class, CHAR);
        addDescriptor(Boolean.class, BOOLEAN);
        addDescriptor(String.class, STRING);
        addDescriptor(UUID.class, UUID);
        addDescriptor(Date.class, DATE);
        addDescriptor(byte[].class, BYTE_ARR);
        addDescriptor(short[].class, SHORT_ARR);
        addDescriptor(int[].class, INT_ARR);
        addDescriptor(long[].class, LONG_ARR);
        addDescriptor(float[].class, FLOAT_ARR);
        addDescriptor(double[].class, DOUBLE_ARR);
        addDescriptor(char[].class, CHAR_ARR);
        addDescriptor(boolean[].class, BOOLEAN_ARR);
        addDescriptor(String[].class, STRING_ARR);
        addDescriptor(UUID[].class, UUID_ARR);
        addDescriptor(Date[].class, DATE_ARR);
        addDescriptor(Object[].class, OBJ_ARR);

        addDescriptor(ArrayList.class, COL);
        addDescriptor(LinkedList.class, COL);
        addDescriptor(HashSet.class, COL);
        addDescriptor(LinkedHashSet.class, COL);
        addDescriptor(TreeSet.class, COL);
        addDescriptor(ConcurrentSkipListSet.class, COL);

        addDescriptor(HashMap.class, MAP);
        addDescriptor(LinkedHashMap.class, MAP);
        addDescriptor(TreeMap.class, MAP);
        addDescriptor(ConcurrentHashMap.class, MAP);

        addDescriptor(GridPortableObjectImpl.class, PORTABLE);

        colTypes.put(ArrayList.class, ARR_LIST);
        colTypes.put(LinkedList.class, LINKED_LIST);
        colTypes.put(HashSet.class, HASH_SET);
        colTypes.put(LinkedHashSet.class, LINKED_HASH_SET);
        colTypes.put(TreeSet.class, TREE_SET);
        colTypes.put(ConcurrentSkipListSet.class, CONC_SKIP_LIST_SET);

        mapTypes.put(HashMap.class, HASH_MAP);
        mapTypes.put(LinkedHashMap.class, LINKED_HASH_MAP);
        mapTypes.put(TreeMap.class, TREE_MAP);
        mapTypes.put(ConcurrentHashMap.class, CONC_HASH_MAP);

        // TODO: Configure from server and client?
        addDescriptor(GridClientAuthenticationRequest.class, 51);
        addDescriptor(GridClientTopologyRequest.class, 52);
        addDescriptor(GridClientTaskRequest.class, 53);
        addDescriptor(GridClientCacheRequest.class, 54);
        addDescriptor(GridClientLogRequest.class, 55);
        addDescriptor(GridClientResponse.class, 56);
        addDescriptor(GridClientNodeBean.class, 57);
        addDescriptor(GridClientNodeMetricsBean.class, 58);
        addDescriptor(GridClientTaskResultBean.class, 59);

        if (portableCfg != null) {
            GridPortableIdMapper globalIdMapper = portableCfg.getIdMapper();
            GridPortableSerializer globalSerializer = portableCfg.getSerializer();

            for (GridPortableTypeConfiguration typeCfg : portableCfg.getTypeConfigurations()) {
                String clsName = typeCfg.getClassName();

                if (clsName == null)
                    throw new GridPortableException("Class name is required for portable type configuration.");

                Class<?> cls;

                try {
                    cls = Class.forName(clsName);
                }
                catch (ClassNotFoundException e) {
                    throw new GridPortableException("Portable class doesn't exist: " + clsName, e);
                }

                GridPortableIdMapper idMapper = globalIdMapper;
                GridPortableSerializer serializer = globalSerializer;

                if (typeCfg.getIdMapper() != null)
                    idMapper = typeCfg.getIdMapper();

                if (typeCfg.getSerializer() != null)
                    serializer = typeCfg.getSerializer();

                addUserTypeDescriptor(cls, idMapper, serializer);
            }
        }
    }

    /** {@inheritDoc} */
    @Nullable @Override public GridPortableClassDescriptor descriptorForClass(Class<?> cls)
        throws GridPortableException {
        assert cls != null;

        GridPortableClassDescriptor desc = descByCls.get(cls);

        if (desc == null) {
            if (Collection.class.isAssignableFrom(cls))
                desc = addCollectionDescriptor(cls);
            else if (Map.class.isAssignableFrom(cls))
                desc = addMapDescriptor(cls);
        }

        return desc;
    }

    /** {@inheritDoc} */
    @Nullable @Override public GridPortableClassDescriptor descriptorForTypeId(boolean userType, int typeId) {
        return descById.get(new DescriptorKey(userType, typeId));
    }

    /** {@inheritDoc} */
    @Override public byte collectionType(Class<? extends Collection> cls) {
        assert cls != null;

        Byte type = colTypes.get(cls);

        return type != null ? type : USER_COL;
    }

    /** {@inheritDoc} */
    @Override public byte mapType(Class<? extends Map> cls) {
        assert cls != null;

        Byte type = mapTypes.get(cls);

        return type != null ? type : USER_COL;
    }

    /** {@inheritDoc} */
    @Override public int fieldId(int typeId, String fieldName) {
        GridPortableIdMapper idMapper = mappers.get(typeId);

        return idMapper != null ? idMapper.fieldId(typeId, fieldName) : fieldName.hashCode();
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeString(out, gridName);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        gridName = U.readString(in);
    }

    /**
     * @return Portable context.
     * @throws ObjectStreamException In case of error.
     */
    protected Object readResolve() throws ObjectStreamException {
        try {
            GridKernal g = GridGainEx.gridx(gridName);

            return g.context().portable().portableContext();
        }
        catch (IllegalStateException e) {
            throw U.withCause(new InvalidObjectException(e.getMessage()), e);
        }
    }

    /**
     * @param cls Class.
     * @throws GridPortableException In case of error.
     */
    private void addDescriptor(Class<?> cls, int typeId) throws GridPortableException {
        GridPortableClassDescriptor desc = new GridPortableClassDescriptor(cls, false, typeId, null, null);

        descByCls.put(cls, desc);
        descById.put(new DescriptorKey(false, typeId), desc);
    }

    /**
     * @param cls Collection class.
     * @return Descriptor.
     * @throws GridPortableException In case of error.
     */
    private GridPortableClassDescriptor addCollectionDescriptor(Class<?> cls) throws GridPortableException {
        assert cls != null;
        assert Collection.class.isAssignableFrom(cls);

        GridPortableClassDescriptor desc = new GridPortableClassDescriptor(cls, false, COL, null, null);

        descByCls.put(cls, desc);

        return desc;
    }

    /**
     * @param cls Map class.
     * @return Descriptor.
     * @throws GridPortableException In case of error.
     */
    private GridPortableClassDescriptor addMapDescriptor(Class<?> cls) throws GridPortableException {
        assert cls != null;
        assert Map.class.isAssignableFrom(cls);

        GridPortableClassDescriptor desc = new GridPortableClassDescriptor(cls, false, MAP, null, null);

        descByCls.put(cls, desc);

        return desc;
    }

    /**
     * @param cls Class.
     * @param idMapper ID mapper.
     * @param serializer Serializer.
     * @throws GridPortableException In case of error.
     */
    public void addUserTypeDescriptor(Class<?> cls, @Nullable GridPortableIdMapper idMapper,
        @Nullable GridPortableSerializer serializer) throws GridPortableException {
        assert cls != null;

        GridPortableId idAnn = cls.getAnnotation(GridPortableId.class);

        int id = 0;

        if (idAnn != null)
            id = idAnn.id();
        else if (idMapper != null)
            id = idMapper.typeId(cls.getName());

        if (id == 0)
            id = cls.getSimpleName().hashCode();

        GridPortableClassDescriptor desc = new GridPortableClassDescriptor(cls, true, id, idMapper, serializer);

        descByCls.put(cls, desc);
        descById.put(new DescriptorKey(true, id), desc);
        mappers.put(id, idMapper);
    }

    /** */
    private static class DescriptorKey {
        /** */
        private final boolean userType;

        /** */
        private final int typeId;

        /**
         * @param userType User type flag.
         * @param typeId Type ID.
         */
        private DescriptorKey(boolean userType, int typeId) {
            this.userType = userType;
            this.typeId = typeId;
        }

        @Override public boolean equals(Object other) {
            if (this == other)
                return true;

            if (other == null || getClass() != other.getClass())
                return false;

            DescriptorKey key = (DescriptorKey)other;

            return userType == key.userType && typeId == key.typeId;
        }

        /** {@inheritDoc} */
        @Override public int hashCode() {
            int res = userType ? 1 : 0;

            res = 31 * res + typeId;

            return res;
        }
    }
}
