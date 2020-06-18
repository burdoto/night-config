package com.electronwill.nightconfig.core;

import com.electronwill.nightconfig.core.utils.MapSupplier;
import com.electronwill.nightconfig.core.utils.StringUtils;
import com.electronwill.nightconfig.core.utils.TransformingMap;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.electronwill.nightconfig.core.utils.StringUtils.single;

/**
 * Base class for configurations. It uses a {@link java.util.Map} to store the config entries.
 *
 * @author TheElectronWill
 */
@SuppressWarnings("unchecked")
public abstract class AbstractConfig implements Config, Cloneable {

	protected final Entry root; // stores the "global" attributes
	protected final Map<String, Entry> storage;
	protected final MapSupplier mapSupplier;

	public AbstractConfig(MapSupplier mapSupplier) {
		this.mapSupplier = mapSupplier;
		this.storage = mapSupplier.get();
		this.root = new Entry(null, this);
	}

	// --- INTERNALS ---

	protected Entry findEntry(String[] path, EntrySearchMode mode) {
		if (path == null) return root;
		Map<String, Entry> parent = findEntryParent(path, mode);
		if (parent == null) return null;
		String leafKey = path[path.length - 1];
		return processLeaf(parent, leafKey, mode);
	}

	/** Finds the map that contains (maybe) the last entry of the path. Array version. */
	private Map<String, Entry> findEntryParent(String[] path, EntrySearchMode mode) {
		Map<String, Entry> current = storage;
		for (int i = 0; i < path.length-1 && current != null; i++) {
			final String key = path[i];
			current = getNextLevel(path, i, current, key, mode);
		}
		return current;
	}

	protected Entry findEntry(Iterable<String> path, EntrySearchMode mode) {
		if (path == null) return root;

		// Find the parent and the leaf key by iterating the path
		String leafKey = null;
		Map<String, Entry> parent = storage;
		Iterator<String> it = path.iterator();
		int i = 0;
		while (parent != null) {
			String key = it.next();
			if (!it.hasNext()) { // the last string is the leaf, not a parent
				leafKey = key;
				break;
			}
			parent = getNextLevel(path, i++, parent, key, mode);
		}

		// Process the entry
		if (parent == null) {
			return null;
		}
		assert leafKey != null;
		return processLeaf(parent, leafKey, mode);
	}

	private Entry processLeaf(Map<String, Entry> parent, String key, EntrySearchMode mode) {
		switch (mode) {
			default: // GET
				return parent.get(key);
			case CREATE:
				Entry leaf = parent.get(key);
				if (leaf == null) {
					leaf = new Entry(key, null);
					parent.put(key, leaf);
				}
				return leaf;
			case DELETE:
				return parent.remove(key);
		}
	}

	private Map<String, Entry> getNextLevel(Object path, int i, Map<String, Entry> current, String key, EntrySearchMode mode) {
		Entry entry = current.get(key);
		if (entry == null) {
			if (mode == EntrySearchMode.CREATE) {
				AbstractConfig sub = createSubConfig();
				entry = new Entry(key, sub);
				current.put(key, entry);
				return sub.storage;
			}
		} else {
			Object value = entry.getValue();
			if (value instanceof AbstractConfig) {
				return ((AbstractConfig)value).storage;
			} else if (mode == EntrySearchMode.CREATE) {
				String p, l;
				if (path instanceof String[]) {
					String[] arrayPath = (String[])path;
					p = StringUtils.pathToString(arrayPath);
					l = StringUtils.pathToString(arrayPath, i);
				} else {
					assert path instanceof Iterable;
					Iterable<String> iterablePath = (Iterable<String>)path;
					p = StringUtils.pathToString(iterablePath);
					l = StringUtils.pathToString(iterablePath, i);
				}
				if (value == null) {
					throw WrongPathException.nullIntermediateLevel(p, l);
				}
				throw WrongPathException.incompatibleIntermediateLevel(p, l, value);
			}
		}
		return null;
	}

	// --- PUBLIC CONFIG METHODS ---

	@Override
	public int size() {
		return storage.size();
	}

	@Override
	public void clear() {
		storage.clear();
	}

	@Override
	public void clearAttributes() {
		storage.forEach((key, data) -> data.clearAttributes());
	}

	@Override
	public void clearComments() {
		storage.forEach((key, data) -> data.remove(StandardAttributes.COMMENT));
	}

	@Override
	public Config.Entry getEntry(String[] path) {
		return findEntry(path, EntrySearchMode.GET);
	}

	@Override
	public Config.Entry getEntry(Iterable<String> path) {
		return findEntry(path, EntrySearchMode.GET);
	}

	@Override
	public Config.Entry addEntry(String[] path) {
		return findEntry(path, EntrySearchMode.CREATE);
	}

	@Override
	public Config.Entry addEntry(Iterable<String> path) {
		return findEntry(path, EntrySearchMode.CREATE);
	}

	@Override
	public void addAll(UnmodifiableConfig config, Depth depth) {
		for (UnmodifiableConfig.Entry other : config.entries()) {
			String key = other.getKey();
			Entry e = storage.get(key);
			if (e == null) {
				storage.put(key, new Entry(other)); // TODO optimize when other instanceof Entry
			} else if (depth == Depth.DEEP) {
				Object eVal = e.getValue();
				Object oVal = other.getValue();
				if (eVal instanceof Config && oVal instanceof UnmodifiableConfig) {
					((Config)eVal).addAll((UnmodifiableConfig)oVal, depth);
				}
			}
		}
	}

	@Override
	public void putAll(UnmodifiableConfig config) {
		for (UnmodifiableConfig.Entry other : config.entries()) {
			String key = other.getKey();
			storage.put(key, new Entry(other));
		}
	}

	@Override
	public void removeAll(UnmodifiableConfig config) {
		for (UnmodifiableConfig.Entry other : config.entries()) {
			storage.remove(other.getKey());
		}
	}

	// --- VALUES ---

	@Override
	public <T> T add(String[] path, Object value) {
		return findEntry(path, EntrySearchMode.CREATE).addValue(value);
	}

	@Override
	public <T> T add(Iterable<String> path, Object value) {
		return findEntry(path, EntrySearchMode.CREATE).addValue(value);
	}

	@Override
	public <T> T set(String[] path, Object value) {
		return findEntry(path, EntrySearchMode.CREATE).setValue(value);
	}

	@Override
	public <T> T set(Iterable<String> path, Object value) {
		return findEntry(path, EntrySearchMode.CREATE).setValue(value);
	}

	@Override
	public <T> T remove(String[] path) {
		Entry prev = findEntry(path, EntrySearchMode.DELETE);
		return prev == null ? null : prev.getValue();
	}

	@Override
	public <T> T remove(Iterable<String> path) {
		Entry prev = findEntry(path, EntrySearchMode.DELETE);
		return prev == null ? null : prev.getValue();
	}


	// --- OTHER ATTRIBUTES ---

	@Override
	public <T> T add(AttributeType<T> attribute, String[] path, T value) {
		return findEntry(path, EntrySearchMode.CREATE).add(attribute, value);
	}

	@Override
	public <T> T add(AttributeType<T> attribute, Iterable<String> path, T value) {
		return findEntry(path, EntrySearchMode.CREATE).add(attribute, value);
	}

	@Override
	public <T> T set(AttributeType<T> attribute, String[] path, T value) {
		return findEntry(path, EntrySearchMode.CREATE).set(attribute, value);
	}

	@Override
	public <T> T set(AttributeType<T> attribute, Iterable<String> path, T value) {
		return findEntry(path, EntrySearchMode.CREATE).set(attribute, value);
	}

	@Override
	public <T> T remove(AttributeType<T> attribute, String[] path) {
		Entry entry = findEntry(path, EntrySearchMode.GET);
		return entry == null ? null : entry.remove(attribute);
	}

	@Override
	public <T> T remove(AttributeType<T> attribute, Iterable<String> path) {
		Entry entry = findEntry(path, EntrySearchMode.GET);
		return entry == null ? null : entry.remove(attribute);
	}


	// --- VIEWS ---

	@Override
	public Map<String, Object> valueMap() {
		BiFunction<String, Entry, Object> read = (k, e) -> e.getValue();
		BiFunction<String, Object, Entry> write = Entry::new;
		Function<Object, Entry> search = o -> o instanceof Entry ? (Entry)o : null;
		return new TransformingMap<>(storage, read, write, search);
	}

	@Override
	public Set<Config.Entry> entries() {
		return new EntrySet();
	}

	public abstract AbstractConfig createSubConfig();

	public abstract AbstractConfig clone();

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof AbstractConfig))
			return false;
		AbstractConfig that = (AbstractConfig)o;
		return storage.equals(that.storage);
	}

	@Override
	public int hashCode() {
		return storage.hashCode();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + ": " + storage;
	}

	@SuppressWarnings("unchecked")
	protected static final class Entry implements Config.Entry, Cloneable {
		private final String key;
		private Object value;
		private Map<AttributeType<?>, Object> attributes = null;

		public Entry(String key, Object value) {
			this.key = key;
			this.value = value;
		}

		public Entry(Entry toCopy) {
			this.key = toCopy.key;
			this.value = toCopy.value;
			this.attributes = new HashMap<>(toCopy.attributes);
		}

		public Entry(UnmodifiableConfig.Entry toCopy) {
			this.key = toCopy.getKey();
			this.value = toCopy.getValue();
			for (UnmodifiableConfig.Attribute<?> attr : toCopy.attributes()) {
				this.getAttributesMap().put(attr.getType(), attr.getValue());
			}
		}

		private Map<AttributeType<?>, Object> getAttributesMap() {
			if (attributes == null) {
				attributes = new HashMap<>();
			}
			return attributes;
		}

		@Override
		public String getKey() {
			return key;
		}

		@Override
		public <T> T getValue() {
			return (T)value;
		}

		@Override
		public <T> T addValue(Object value) {
			T old = (T)this.value;
			if (old == null) {
				this.value = value;
			}
			return old;
		}

		@Override
		public <T> T setValue(Object value) {
			T old = (T)this.value;
			this.value = value;
			return old;
		}

		@Override
		public <T> T removeValue() {
			return setValue(null);
		}

		@Override
		public <T> T set(AttributeType<T> attribute, T value) {
			return (T) getAttributesMap().put(attribute, value);
		}

		@Override
		public <T> T add(AttributeType<T> attribute, T value) {
			return (T) getAttributesMap().putIfAbsent(attribute, value);
		}

		@Override
		public <T> T remove(AttributeType<T> attribute) {
			return attributes == null ? null : (T) attributes.remove(attribute);
		}

		@Override
		public boolean has(AttributeType<?> attribute) {
			return attributes != null && attributes.containsKey(attribute);
		}

		@Override
		public <T> T get(AttributeType<T> attribute) {
			return attributes == null ? null : (T) attributes.get(attribute);
		}

		@Override
		public void clearAttributes() {
			attributes = null;
		}

		@Override
		public Iterable<Config.Attribute<?>> attributes() {
			return AttributesIterator::new;
		}

		@Override
		public String toString() {
			if (attributes == null) {
				return String.valueOf(value);
			}
			return String.format("%s = %s with attributes %s", key, value, attributes);
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Entry)) return false;
			Entry entry = (Entry)o;
			return Objects.equals(key, entry.key) &&
				   Objects.equals(value, entry.value) &&
				   Objects.equals(attributes, entry.attributes);
		}

		@Override
		public int hashCode() {
			return Objects.hash(key, value, attributes);
		}

		@Override
		public <T> Map.Entry<String, T> toMapEntry() {
			return new Map.Entry<String, T>() {
				@Override
				public String getKey() {
					return Entry.this.getKey();
				}

				@Override
				public T getValue() {
					return Entry.this.getValue();
				}

				@Override
				public T setValue(T value) {
					return Entry.this.setValue(value);
				}
			};
		}

		private final class AttributesIterator implements Iterator<Attribute<?>> {
			private final Iterator<Map.Entry<AttributeType<?>, Object>> entryIterator =
				attributes == null ? null : attributes.entrySet().iterator();

			@Override
			public boolean hasNext() {
				return entryIterator != null && entryIterator.hasNext();
			}

			@Override
			public Config.Attribute<?> next() {
				if (entryIterator == null) throw new NoSuchElementException();
				final Map.Entry<AttributeType<?>, Object> entry = entryIterator.next();
				return new Config.Attribute<Object>() {
					@Override
					public Object setValue(Object value) {
						return entry.setValue(value);
					}

					@Override
					public AttributeType<Object> getType() {
						return (AttributeType<Object>)entry.getKey();
					}

					@Override
					public Object getValue() {
						return entry.getValue();
					}
				};
			}
		}
	}

	protected final class EntrySet implements Set<Config.Entry> {
		@Override
		public int size() {
			return storage.size();
		}

		@Override
		public boolean isEmpty() {
			return storage.isEmpty();
		}

		@Override
		public boolean contains(Object o) {
			if (o instanceof Entry) {
				Entry query = (Entry)o;
				Entry data = storage.get(query.getKey());
				return query.equals(data);
			}
			return false;
		}

		@Override
		@SuppressWarnings("rawtypes")
		public Iterator<Config.Entry> iterator() {
			Iterator<Entry> it = storage.values().iterator();
			return (Iterator)it; // it's a shame Iterator isn't covariant!
		}

		@Override
		public Object[] toArray() {
			return storage.values().toArray();
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return storage.values().toArray(a);
		}

		@Override
		public boolean add(Config.Entry entry) {
			return AbstractConfig.this.add(single(entry.getKey()), entry.getValue()) == null;
		}

		@Override
		public boolean remove(Object o) {
			if (o instanceof Config.Entry) {
				Config.Entry query = (Config.Entry)o;
				return AbstractConfig.this.remove(single(query.getKey())) != null;
			}
			return false;
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return storage.values().containsAll(c);
		}

		@Override
		public boolean addAll(Collection<? extends Config.Entry> c) {
			boolean changed = false;
			for (Config.Entry entry : c) {
				changed = add(entry) || changed;
			}
			return changed;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return storage.values().retainAll(c);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return storage.values().removeAll(c);
		}

		@Override
		public void clear() {
			AbstractConfig.this.clear();
		}
	}

	protected static enum EntrySearchMode {
		GET, CREATE, DELETE
	}
}
