import java.io.*;
import java.util.*;

public class Renamer {
	public static void main(String[] args) throws Exception {
		InputStream in = new FileInputStream(args[0]);
		OutputStream out = new FileOutputStream(args[1]);
		BufferedReader rf = new BufferedReader(new FileReader(args[2]));
		String line;
		Map<String, String> replacements = new HashMap<String, String>();
		while((line = rf.readLine()) != null) {
			String from = line.substring(0, line.indexOf('\t'));
			String to = line.substring(line.indexOf('\t') + 1);
			replacements.put(from.trim(), to.trim());
		}
		patch(in, out, replacements);
		out.close();
		in.close();
	}

	public static void patch(InputStream in, OutputStream out, Map<String, String> replacements) throws IOException {
		byte[] buf = new byte[256];

		// copy header
		in.read(buf, 0, 8);
		out.write(buf, 0, 8);

		// load constant pool
		ConstantPool constants = new ConstantPool(in);

		// patch NameAndType entries
		for(ConstantPool.Entry entry : constants) {
			if(entry instanceof ConstantPool.Utf8Entry) {
				ConstantPool.Utf8Entry string = (ConstantPool.Utf8Entry) entry;
				String value = string.getValue();
				if(replacements.containsKey(value)) {
					String replacement = replacements.get(value);
					System.out.printf("%s -> %s\n", value, replacement);
					string.setValue(replacement);
				}
			}
			/*
			if(entry instanceof ConstantPool.NameAndTypeEntry) {
				ConstantPool.NameAndTypeEntry nameAndType = (ConstantPool.NameAndTypeEntry) entry;
				ConstantPool.Utf8Entry string = nameAndType.getName();
				String identifier = string.getValue();
				if(replacements.containsKey(identifier)) {
					String replacement = replacements.get(identifier);
					System.out.printf("%s -> %s\n", identifier, replacement);
					string.setValue(replacement);
				}
			}
			*/
		}

		// write constant pool
		constants.write(out);

		// copy rest
		int n;
		while((n = in.read(buf)) != -1) {
			out.write(buf, 0, n);
		}
	}

	static class ConstantPool implements Iterable<ConstantPool.Entry> {
		public final static int	CONSTANT_Class = 7;
		public final static int CONSTANT_Fieldref = 9;
		public final static int CONSTANT_Methodref = 10;
		public final static int CONSTANT_InterfaceMethodref = 11;
		public final static int CONSTANT_String = 8;
		public final static int CONSTANT_Integer = 3;
		public final static int CONSTANT_Float = 4;
		public final static int CONSTANT_Long = 5;
		public final static int CONSTANT_Double = 6;
		public final static int CONSTANT_NameAndType = 12;
		public final static int CONSTANT_Utf8 = 1;
		public final static int CONSTANT_MethodHandle = 15;
		public final static int CONSTANT_MethodType = 16;
		public final static int CONSTANT_InvokeDynamic = 18;

		public Map<Integer, Integer> lengths;

		private Entry[] entries;

		public ConstantPool() {
			lengths = new HashMap<Integer, Integer>();
			lengths.put(CONSTANT_Class, 2);
			lengths.put(CONSTANT_Fieldref, 4);
			lengths.put(CONSTANT_Methodref, 4);
			lengths.put(CONSTANT_InterfaceMethodref, 4);
			lengths.put(CONSTANT_String, 2);
			lengths.put(CONSTANT_Integer, 4);
			lengths.put(CONSTANT_Float, 4);
			lengths.put(CONSTANT_Long, 8);
			lengths.put(CONSTANT_Double, 8);
			lengths.put(CONSTANT_MethodHandle, 3);
			lengths.put(CONSTANT_MethodType, 2);
			lengths.put(CONSTANT_InvokeDynamic, 4);
		}

		public ConstantPool(InputStream in) throws IOException {
			this();
			read(in);
		}

		public void read(InputStream in) throws IOException {
			DataInputStream i = new DataInputStream(in);
			entries = new Entry[i.readUnsignedShort()];
			for(int n = 1; n < entries.length; n++) {
				entries[n] = readEntry(i);
			}
		}

		public void write(OutputStream out) throws IOException {
			DataOutputStream o = new DataOutputStream(out);
			out.write((byte) (entries.length >> 8));
			out.write((byte) entries.length);
			for(int i = 1; i < entries.length; i++) {
				entries[i].write(o);
			}
			o.flush();
		}

		private Entry readEntry(DataInput in) throws IOException {
			int type = in.readByte() & 0xFF;
			if(lengths.containsKey(type)) {
				byte[] data = new byte[lengths.get(type)];
				in.readFully(data);
				return new Entry(type, data);
			} else if(type == CONSTANT_NameAndType) {
				return new NameAndTypeEntry(in);
			} else if(type == CONSTANT_Utf8) {
				return new Utf8Entry(in);
			}
			throw new RuntimeException("illegal data: " + type);
		}

		public int size() {
			return entries.length;
		}

		public Entry get(int id) {
			return entries[id];
		}

		public void set(int id, Entry entry) {
			entries[id] = entry;
		}

		class Entry {
			private int type;
			private byte[] data;

			public Entry() {
				this(-1, null);
			}

			public Entry(int type) {
				this(type, null);
			}

			public Entry(int type, byte[] data) {
				this.type = type;
				this.data = data;
			}

			public int getEntryType() {
				return type;
			}

			protected void serialize(DataOutput out) throws IOException {
				out.write(data);
			}

			public void write(DataOutput out) throws IOException {
				out.write(type);
				serialize(out);
			}
		}

		class NameAndTypeEntry extends Entry {
			private int name;
			private int type;

			public NameAndTypeEntry(int name, int type) {
				super(CONSTANT_NameAndType);
				this.name = name;
				this.type = type;
			}

			public NameAndTypeEntry(DataInput in) throws IOException {
				super(CONSTANT_NameAndType);
				read(in);
			}

			public int getNameId() {
				return name;
			}

			public Utf8Entry getName() {
				return (Utf8Entry) get(name);
			}

			public int getTypeId() {
				return type;
			}

			public Utf8Entry getType() {
				return (Utf8Entry) get(type);
			}

			public void read(DataInput in) throws IOException {
				name = in.readUnsignedShort();
				type = in.readUnsignedShort();
			}

			@Override
			protected void serialize(DataOutput out) throws IOException {
				out.writeShort(name);
				out.writeShort(type);
			}
		}

		class Utf8Entry extends Entry {
			private String value;

			public Utf8Entry(String value) {
				super(CONSTANT_Utf8);
				this.value = value;
			}

			public Utf8Entry(DataInput in) throws IOException {
				super(CONSTANT_Utf8);
				read(in);
			}

			public String getValue() {
				return value;
			}

			public void setValue(String value) {
				this.value = value;
			}

			public void read(DataInput in) throws IOException {
				value = in.readUTF();
			}

			@Override
			protected void serialize(DataOutput out) throws IOException {
				out.writeUTF(value);
			}
		}

		public Iterator<Entry> iterator() {
			return new Iterator<Entry>() {
				private int pos = 0;

				@Override
				public Entry next() {
					if(pos == entries.length) {
						throw new NoSuchElementException();
					}
					return entries[pos++];
				}

				@Override
				public boolean hasNext() {
					return pos < entries.length;
				}

				@Override
				public void remove() {
					throw new UnsupportedOperationException();
				}
			};
		}
	}
}
