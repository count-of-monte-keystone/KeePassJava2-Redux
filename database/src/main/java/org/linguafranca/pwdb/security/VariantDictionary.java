package org.linguafranca.pwdb.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.Immutable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.linguafranca.pwdb.security.VariantDictionary.EntryType.UINT32;
import static org.linguafranca.pwdb.security.VariantDictionary.EntryType.ARRAY;
import static org.linguafranca.pwdb.security.VariantDictionary.EntryType.UINT64;

/**
 * Implementation of a storage for V4 KDBX Header field parameters
 * <p>
 * Though specific to KDBX V4 it is kept here for convenience of parameter passing to crypto functions
 */

@SuppressWarnings("WeakerAccess")
public class VariantDictionary {

    private final short version;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    private final static String knn = "VariantDictionary key must not be null";
    private final static String vnn = "VariantDictionary.Entry value must not be null";

    /**
     * The list of permissible entry types
     */
    @SuppressWarnings("unused")
    public enum EntryType {
        UINT32(0x4),
        UINT64(0x5),
        BOOL(0x8),
        INT32(0xC),
        INT64(0xD),
        STRING(0x18), // UTF-8, without BOM, without null terminator
        ARRAY(0x42);

        private final byte value;

        EntryType(int b) {
            this.value = (byte) b;
        }

        public static EntryType get(byte type) {
            for (EntryType et : values()) {
                if (et.value == type) {
                    return et;
                }
            }
            throw new IllegalArgumentException("Unknown Variant Dictionary Type " + String.format("%x", type));
        }
    }

    @SuppressWarnings("WeakerAccess")
    @Immutable
    public static class Entry {
        private final byte type;
        private final @NotNull byte[] value;
        private final ByteOrder byteOrder;

        public Entry(EntryType entryType, @NotNull byte[] value) {
            this(entryType, value, ByteOrder.LITTLE_ENDIAN);
        }

        public Entry(EntryType entryType, @NotNull byte[] value, ByteOrder byteOrder) {
            this.type = entryType.value;
            this.value = checkNotNull(value, vnn);
            this.byteOrder = byteOrder;
        }

        public byte getType() {
            return type;
        }
        
        public int getValueLength() {
        	return value.length;
        }

        public UUID asUuid() {
            if (value.length == 16) {
                ByteBuffer b = ByteBuffer.wrap(value);
                return new UUID(b.getLong(), b.getLong(8));
            }
            throw new IllegalStateException("Cannot convert value to UUID");
        }

        public long asLong() {
            if (value.length != 8) {
                throw new IllegalStateException("Cannot convert value to long");
            }
            return ByteBuffer.wrap(value).order(byteOrder).getLong();
        }

        public int asInteger() {
            if (value.length != 4) {
                throw new IllegalStateException("Cannot convert value to int");
            }
            return ByteBuffer.wrap(value).order(byteOrder).getInt();
        }

        public @NotNull byte[] asByteArray() {
            return value;
        }
    }

    /**
     * Make a new Variant Dictionary whose version must be 1
     */
    public VariantDictionary(short version) {
        if (version != 1) {
            throw new IllegalArgumentException("Variant Dictionary version must be 1");
        }
        this.version = version;
    }

    /**
     * Make a copy of this structure - Entries are immutable so are copied as is
     */
    public VariantDictionary copy() {
        VariantDictionary vd = new VariantDictionary(this.version);
        for (Map.Entry<String, VariantDictionary.Entry> e : this.entries.entrySet()) {
            vd.entries.put(e.getKey(), e.getValue());
        }
        return vd;
    }

    /**
     * Get the version number of this structure
     *
     * @return 1
     */
    public short getVersion() {
        return version;
    }
    
    /*
     * Get a Set containing the String keys 
     */
    public Set<String> getKeys() {
    	return entries.keySet();
    }
    
    /**
     * Get the String key of a Entry object
     * @param entry the entry object to get the key name of
     * @return the String key name of entry or an empty String if no key was found
     */
    public String getKeyOfEntry(Entry entry) {
    	String keyName = "";
    	for(String key : entries.keySet()) {
    		if(entries.get(key) == entry) {
    			keyName = key;
    			break;
    		}	
    	}
    	return keyName;
    }
      
    /**
     * Get the total bytes that comprise the variant dictionary
     * 
     * @return the total size (in bytes) of the variant dictionary
     */
    public int getTotalBytes() {
    	int bytes = 3; // version bytes (2) + null terminator byte (1)
    	for(Entry entry : entries.values()) {
    		int valueTypeBytes = 1;
    		int keyNameLengthBytes = 4;
    		int keyNameBytes = getKeyOfEntry(entry).length();
    		int valueLengthBytes = 4;
    		int valueBytes = entry.getValueLength();
    		bytes += (valueTypeBytes+keyNameLengthBytes+keyNameBytes+valueLengthBytes+valueBytes);
    	}
    	return bytes;
    }
   
    /**
     * Return an entry for the key supplied
     *
     * @param key the key
     * @return an entry, or null if no such entry exists
     */
    public @Nullable Entry get(@NotNull String key) {
        return entries.get(key);
    }

    /**
     * ensure that the entry sought is not null, by throwing an illegal argument exception if it is not present
     *
     * @param key the key to get
     * @return the entry corresponding to the key
     */
    public @NotNull Entry mustGet(@NotNull String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            throw new IllegalArgumentException("There is no entry with key " + key);
        }
        return entry;
    }

    /**
     * Add an entry of the type defined
     *
     * @param key   the entry key
     * @param type  the data type of the entry
     * @param value a buffer containing an appropriate entry
     */
    public void put(@NotNull String key, EntryType type, @NotNull byte[] value) {
        entries.put(checkNotNull(key), new Entry(type, checkNotNull(value)));
    }

    /**
     * Put a UUID under the key defined
     */
    public void putUuid(@NotNull String key, UUID uuid) {
        byte[] buf = new byte[16];
        ByteBuffer bb = ByteBuffer.wrap(buf);
        bb.putLong(0, uuid.getMostSignificantBits());
        bb.putLong(8, uuid.getLeastSignificantBits());
        entries.put(checkNotNull(key, knn), new Entry(ARRAY, buf));
    }

    /**
     * Put a byte array under the key defined
     */
    public void putByteArray(@NotNull String key, @NotNull byte[] value) {
        entries.put(checkNotNull(key, knn), new Entry(ARRAY, value));
    }

    /**
     * Put an unsigned/signed long (aka 64-bit int) under the key defined
     */
    public void putLong(@NotNull String key, long value) {
        byte[] buf = new byte[8];
        ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
        bb.putLong(value);
        entries.put(checkNotNull(key, knn), new Entry(UINT64, buf));
    }

    /**
     * Put an unsigned/signed 32-bit int under the key defined
     */
	public void putInt(@NotNull String key, int value) {
		byte[] buf = new byte[4];
		ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
		bb.putInt(value);
		entries.put(checkNotNull(key, knn), new Entry(UINT32, buf));
	}
}
