package nick1st.gson;

import com.google.gson.*;
import mcjty.lostcities.LostCities;

import javax.annotation.Nonnull;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * An advanced {@link JsonDeserializer} Implementation powered by reflection, adding the abilities to <br>
 * - inherit a field from another object. <br>
 * - fill in default values if a fields reference is {@link Void}. Supported Types: See SUPPORTED_TYPES. See {@link DefaultValue}. <br>
 * - mark a field as required. It'll throw an error if the Field isn't contained in the json. See {@link RequiredField}. <br>
 * - specify a range for numbers. It'll throw an error if the Fields value is out of range. See {@link ValueFloatRange}, {@link ValueIntRange}. <br>
 * <br>
 * To function correctly you should NOT use primitives (as they can not be null). Use their wrapper types instead. <br>
 * <br>
 * You will need to register this deserializer through GsonBuilder.registerTypeAdapter(Type, new AdvancedJsonDeserializer<Type>()). <br>
 * <br>
 *
 * @param <T> Type to parse to.
 */
public class AdvancedJsonDeserializer<T> implements JsonDeserializer<T> {

    private static final List<Class<?>> SUPPORTED_TYPES = new ArrayList<Class<?>>() {{
        add(Byte.class);
        add(Short.class);
        add(Integer.class);
        add(Long.class);
        add(Float.class);
        add(Double.class);
        add(Boolean.class);
        add(Character.class);
        add(String.class);
        add(URI.class);
    }};

    private final Object inheritOf;

    /**
     * @param inherit Object to inherit from.
     */
    public AdvancedJsonDeserializer(Object inherit) {
        inheritOf = inherit;
    }

    /**
     * Called to deserialize json.
     *
     * @param je   Source json string.
     * @param type Object's type.
     * @param jdc  Unused.
     * @return Parsed object.
     * @throws JsonParseException When there is an error while parsing.
     */
    @Override
    public T deserialize(JsonElement je, Type type, JsonDeserializationContext jdc)
            throws JsonParseException {
        // Parsing as usual.
        LostCities.setup.getLogger().debug("Parsing JSON");
        T pojo = new Gson().fromJson(je, type);

        // Subclass
        LostCities.setup.getLogger().debug("Subclassing JSON");
        subClass(pojo.getClass().getDeclaredFields(), pojo);

        // Inherit from object
        LostCities.setup.getLogger().debug("Inheriting");
        inherit(pojo.getClass().getDeclaredFields(), pojo, inheritOf);

        // Fill default values in fields annotated with @DefaultValue("")
        LostCities.setup.getLogger().debug("Fill default fields");
        fillDefaultFields(pojo.getClass().getDeclaredFields(), pojo);

        // Check value ranges
        LostCities.setup.getLogger().debug("Validate Ranges");
        checkValueRanges(pojo.getClass().getDeclaredFields(), pojo);

        // Getting all fields of the class and checking if all required ones were provided.
        LostCities.setup.getLogger().debug("Check required fields");
        checkRequiredFields(pojo.getClass().getDeclaredFields(), pojo);

        // Checking if all required fields of parent classes were provided.
        LostCities.setup.getLogger().debug("Check super classes");
        checkSuperClasses(pojo);

        // All checks are ok.
        return pojo;
    }

    // Walk through all subclasses of pojo and create instances if they have null references but have fields annotated with default or required.
    private boolean subClass(@Nonnull Field[] fields, @Nonnull Object pojo) throws JsonParseException {
        boolean result = false;
        // Check nested items.
        if (pojo instanceof List) {
            final List<?> pojoList = (List<?>) pojo;
            for (final Object pojoListPojo : pojoList) {
                subClass(pojoListPojo.getClass().getDeclaredFields(), pojoListPojo);
            }
        }

        for (Field f : fields) {
            try {
                // Set field accessible and check that it has value.
                f.setAccessible(true);
                Object fieldObject = f.get(pojo);
                if (!SUPPORTED_TYPES.contains(f.getType())) { //&& fieldObject != null
                    if (fieldObject == null) {
                        Constructor<?> constructor = f.getType().getDeclaredConstructor();
                        constructor.setAccessible(true);
                        fieldObject = constructor.newInstance();
                    }
                    if (subClass(fieldObject.getClass().getDeclaredFields(), fieldObject)) {
                        LostCities.setup.getLogger().trace("Subclassing required " + pojo);
                        f.set(pojo, fieldObject);
                        result = true;
                        LostCities.setup.getLogger().trace("Printing Pojo: " + pojo);
                    }
                    // Check field for default or required value annotation.
                } else if (f.getAnnotation(DefaultValue.class) != null || f.getAnnotation(RequiredField.class) != null) {
                    result = true;
                }
                // Exceptions thrown by reflection.
            } catch (IllegalAccessException | NoSuchMethodException | InstantiationException | InvocationTargetException e) {
                throw new JsonParseException(e);
            }
        }
        return result;
    }

    // Inherit from "super object"
    private void inherit(@Nonnull Field[] fields, @Nonnull Object pojo, Object inheritOf) throws JsonParseException {
        if (inheritOf == null) {
            return;
        }

        if (pojo.getClass() != inheritOf.getClass()) {
            throw new JsonParseException("Can only inherit from object of same type: Have " + String.format("%1$s, but should inherit from %2$s",
                    pojo.getClass().getSimpleName(),
                    inheritOf.getClass().getSimpleName()));
        }

        // Check nested items.
        if (pojo instanceof List) {
            final List<?> pojoList = (List<?>) pojo;
            for (final Object pojoListPojo : pojoList) {
                inherit(pojoListPojo.getClass().getDeclaredFields(), pojoListPojo, inheritOf);
            }
        }

        for (Field f : fields) {
            try {
                // Set field accessible and check that it has value.
                f.setAccessible(true);
                Object fieldObject = f.get(pojo);
                // Check field values.
                if (fieldObject == null) {
                    Field fi = (Field) (Arrays.stream(inheritOf.getClass().getDeclaredFields()).filter(field -> (field.getName().equals(f.getName()))).limit(1).toArray())[0];
                    fi.setAccessible(true);
                    Object fieldObjectInherit = fi.get(inheritOf);
                    f.set(pojo, fieldObjectInherit);
                } else if (!SUPPORTED_TYPES.contains(f.getType())) {
                    Field fi = (Field) (Arrays.stream(inheritOf.getClass().getDeclaredFields()).filter(field -> (field.getName().equals(f.getName()))).limit(1).toArray())[0];
                    fi.setAccessible(true);
                    Object fieldObjectInherit = fi.get(inheritOf);
                    inherit(fieldObject.getClass().getDeclaredFields(), fieldObject, fieldObjectInherit);
                }
                // Exceptions thrown by reflection.
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new JsonParseException(e);
            }
        }
    }

    private void fillDefaultFields(@Nonnull Field[] fields, @Nonnull Object pojo)
            throws JsonParseException {
        // Check nested items.
        if (pojo instanceof List) {
            final List<?> pojoList = (List<?>) pojo;
            for (final Object pojoListPojo : pojoList) {
                fillDefaultFields(pojoListPojo.getClass().getDeclaredFields(), pojoListPojo);
            }
        }

        for (Field f : fields) {
            try {
                // Set field accessible and check that it has value.
                f.setAccessible(true);
                Object fieldObject = f.get(pojo);
                // Check field for default value annotation.
                if (f.getAnnotation(DefaultValue.class) != null) {
                    if (fieldObject == null) {
                        // Prepare default Value
                        String defaultValue = f.getAnnotation(DefaultValue.class).value();
                        if (defaultValue.isEmpty()) {
                            throw new JsonParseException("Annotation Value is empty: " + String.format("%1$s.%2$s",
                                    pojo.getClass().getSimpleName(),
                                    f.getName()));
                        }
                        //Parse Table below
                        Object value = null;
                        if (f.getType() == Byte.class) {
                            value = Byte.parseByte(defaultValue);
                        } else if (f.getType() == Short.class) {
                            value = Short.parseShort(defaultValue);
                        } else if (f.getType() == Integer.class) {
                            f.set(pojo, Integer.parseInt(defaultValue));
                        } else if (f.getType() == Long.class) {
                            value = Long.parseLong(defaultValue);
                        } else if (f.getType() == Float.class) {
                            value = Float.parseFloat(defaultValue);
                        } else if (f.getType() == Double.class) {
                            value = Double.parseDouble(defaultValue);
                        } else if (f.getType() == Boolean.class) {
                            value = Boolean.parseBoolean(defaultValue);
                        } else if (f.getType() == Character.class) {
                            value = defaultValue.charAt(0);
                        } else if (f.getType() == String.class) {
                            value = defaultValue;
                        } else if (f.getType() == URI.class) {
                            value = URI.create(defaultValue);
                        }
                        f.set(pojo, value);
                    }
                } else if (!SUPPORTED_TYPES.contains(f.getType()) && fieldObject != null) { //&& fieldObject != null
                    fillDefaultFields(fieldObject.getClass().getDeclaredFields(), fieldObject);
                }
                // Exceptions thrown by reflection.
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new JsonParseException(e);
            }
        }
    }

    /**
     * @throws JsonParseException When some field value is out of range.
     * @apiNote Skips null values.
     */
    private void checkValueRanges(@Nonnull Field[] fields, @Nonnull Object pojo)
            throws JsonParseException {
        // Check nested items.
        if (pojo instanceof List) {
            final List<?> pojoList = (List<?>) pojo;
            for (final Object pojoListPojo : pojoList) {
                checkValueRanges(pojoListPojo.getClass().getDeclaredFields(), pojoListPojo);
            }
        }

        for (Field f : fields) {
            try {
                // Set field accessible and check that it has value.
                f.setAccessible(true);
                Object fieldObject = f.get(pojo);
                // Check field for default value annotation.
                if (f.getAnnotation(ValueIntRange.class) != null || f.getAnnotation(ValueFloatRange.class) != null) {
                    if (fieldObject != null) {
                        if (fieldObject instanceof Float || fieldObject instanceof Double) {
                            double fieldObject1;
                            if (fieldObject instanceof Double) {
                                fieldObject1 = (double) fieldObject;
                            } else {
                                fieldObject1 = ((Float) fieldObject).doubleValue();
                            }
                            if (f.getAnnotation(ValueFloatRange.class).minValue() > fieldObject1 || f.getAnnotation(ValueFloatRange.class).maxValue() < fieldObject1) {
                                throw new JsonParseException("Field value is out of range. Got " + fieldObject1 + ", but range is " + f.getAnnotation(ValueFloatRange.class).minValue()
                                        + " - " + f.getAnnotation(ValueFloatRange.class).maxValue() + " at: " + String.format("%1$s.%2$s",
                                        pojo.getClass().getSimpleName(),
                                        f.getName()));
                            }
                        } else if (fieldObject instanceof Number) {
                            long fieldObject1;
                            if (fieldObject instanceof Long) {
                                fieldObject1 = (long) fieldObject;
                            } else if (fieldObject instanceof Integer) {
                                fieldObject1 = ((Integer) fieldObject).longValue();
                            } else if (fieldObject instanceof Short) {
                                fieldObject1 = ((Short) fieldObject).longValue();
                            } else {
                                fieldObject1 = ((Byte) fieldObject).longValue();
                            }
                            if (f.getAnnotation(ValueIntRange.class).minValue() > fieldObject1 || f.getAnnotation(ValueIntRange.class).maxValue() < fieldObject1) {
                                throw new JsonParseException("Field value is out of range. Got " + fieldObject1 + ", but range is " + f.getAnnotation(ValueIntRange.class).minValue()
                                        + " - " + f.getAnnotation(ValueIntRange.class).maxValue() + " at: " + String.format("%1$s.%2$s",
                                        pojo.getClass().getSimpleName(),
                                        f.getName()));
                            }
                        } else {
                            throw new JsonParseException("Annotated field is not an instance of Number: " + String.format("%1$s.%2$s",
                                    pojo.getClass().getSimpleName(),
                                    f.getName()));
                        }
                    }
                } else if (!SUPPORTED_TYPES.contains(f.getType()) && fieldObject != null) {
                    checkValueRanges(fieldObject.getClass().getDeclaredFields(), fieldObject);
                }
                // Exceptions thrown by reflection.
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new JsonParseException(e);
            }
        }
    }

    /**
     * @throws JsonParseException When some required field was not met.
     */
    private void checkRequiredFields(@Nonnull Field[] fields, @Nonnull Object pojo)
            throws JsonParseException {
        // Check nested items.
        if (pojo instanceof List) {
            final List<?> pojoList = (List<?>) pojo;
            for (final Object pojoListPojo : pojoList) {
                checkRequiredFields(pojoListPojo.getClass().getDeclaredFields(), pojoListPojo);
                checkSuperClasses(pojoListPojo);
            }
        }

        for (Field f : fields) {
            try {
                // Set field accessible and check that it has value.
                f.setAccessible(true);
                Object fieldObject = f.get(pojo);
                // Check field for required annotation.
                if (f.getAnnotation(RequiredField.class) != null) {

                    if (fieldObject == null) {
                        // Required field is null - throwing error.
                        throw new JsonParseException("Required field is empty: " + String.format("%1$s.%2$s",
                                pojo.getClass().getSimpleName(),
                                f.getName()));
                    } else {
                        checkRequiredFields(fieldObject.getClass().getDeclaredFields(), fieldObject);
                        checkSuperClasses(fieldObject);
                    }

                }

                // Exceptions thrown by reflection.
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new JsonParseException(e);
            }
        }
    }

    /**
     * @throws JsonParseException When some required field was not met.
     */
    private void checkSuperClasses(@Nonnull Object pojo) throws JsonParseException {
        Class<?> superclass = pojo.getClass();
        while ((superclass = superclass.getSuperclass()) != null) {
            checkRequiredFields(superclass.getDeclaredFields(), pojo);
        }
    }

    /**
     * Marks required fields.
     * Parsing will be failed if these fields won't be provided.
     */
    @Retention(RetentionPolicy.RUNTIME) // to make reading of this field possible at the runtime
    @Target(ElementType.FIELD)          // to make annotation accessible through reflection
    public @interface RequiredField {
    }

    /**
     * Marks and sets default values for fields:
     * Empty Fields will get filled.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface DefaultValue {
        String value();
    }

    /**
     * Marks and sets value ranges for fields:
     * Parsing will be failed if the fields value is not within this range
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface ValueIntRange {
        long minValue();

        long maxValue();
    }

    /**
     * Marks and sets value ranges for fields:
     * Parsing will be failed if the fields value is not within this range
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface ValueFloatRange {
        double minValue();

        double maxValue();
    }

}