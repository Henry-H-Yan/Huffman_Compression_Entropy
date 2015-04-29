import java.io.IOException;
/** @author 
 *  Henry Yan & Zhen Zhang
 */

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;



public class SimpleHuffProcessor implements IHuffProcessor {

	private HuffViewer myViewer;
	private Map<Integer, String> myMap;
	private int[] count;
	private TreeNode root;
	private int headerCount=0;
	private int myBits=0;

	public int compress(InputStream in, OutputStream out, boolean force)
			throws IOException {

		int number = 0;
		BitOutputStream bitsOut = new BitOutputStream(out);
		BitInputStream input = new BitInputStream(in);

		bitsOut.writeBits(BITS_PER_INT,MAGIC_NUMBER);
		for(int o=0;o<count.length;o++){
			bitsOut.writeBits(BITS_PER_INT, count[o]);
		}

		if(myBits<headerCount && !force){
			throw new IOException("compressed filer larger and no force compression");
		}
		int currentWord = input.readBits(IHuffConstants.BITS_PER_WORD);

		while (currentWord != -1) {
			if (myMap.get(currentWord) == null) {
				System.out.println(currentWord);
			}


			for (int i = 0; i < myMap.get(currentWord).length(); i++) {
				char bit = myMap.get(currentWord).charAt(i);
				if (bit == '0') {
					bitsOut.writeBits(1, 0);
				} else {
					bitsOut.writeBits(1, 1);
				}
				number++;
			}
			currentWord = input.readBits(IHuffConstants.BITS_PER_WORD);
		}
		for (int i = 0; i < myMap.get(PSEUDO_EOF).length(); i++) {
			char bit = myMap.get(PSEUDO_EOF).charAt(i);
			if (bit == '0') {
				bitsOut.writeBits(1, 0);
			} else {
				bitsOut.writeBits(1, 1);
			}
		}

		bitsOut.close();
		return number;
	}

	public int preprocessCompress(InputStream in) throws IOException {

		int number = 0;
		count = countLetters(in);
		root = buildhuff(count);
		Map<Integer, String> mapping = new HashMap<Integer, String>();
		number = makemap(mapping, root, "", number);
		myMap = mapping;
		myBits=number; // pass to global variable
		return number;
	}

	//write out tree
	public void writeTree(TreeNode root, BitOutputStream out){
		if(root.isLeaf()){
			out.writeBits(1, 1);
			headerCount++;
			out.writeBits(BITS_PER_WORD+1, root.myValue);
			headerCount=headerCount+9;
		}
		if(!root.isLeaf()){
			out.writeBits(1, 0);
			headerCount++;
			writeTree(root.myLeft,out);
			writeTree(root.myRight,out);
		}

	}


	public int makemap(Map<Integer, String> mapping, TreeNode root,
			String string, int number) {
		if (root.myLeft == null && root.myRight == null) {
			mapping.put(root.myValue, string);
			return number + (8 - string.length()) * root.myWeight;
		}
		int num1 = makemap(mapping, root.myLeft, string + "0", number + 1);
		int num2 = makemap(mapping, root.myRight, string + "1", number + 1);
		return num1 + num2;
	}

	public String treeToString(TreeNode root) {
		return treeHelper (root, "", "");   //invoke helper method

	}

	public String treeHelper(TreeNode root, String string, String string2) {
		// TODO Auto-generated method stub
		if (root == null) {
			return "";
		}

		string = "\n" + string2 + root.myWeight + "/" + root.myValue;
		string += treeHelper(root.myLeft, string, string2 + " ");
		string += treeHelper(root.myRight, string, string2 + " ");

		return string;

	}

	public TreeNode buildhuff(int[] array) {
		// TODO Auto-generated method stub
		PriorityQueue<TreeNode> pq = new PriorityQueue<TreeNode>();

		for (int i = 0; i < array.length; i++) {
			if (array[i] > 0)
				pq.add(new TreeNode(i, array[i]));
		}
		pq.add(new TreeNode(PSEUDO_EOF, 1));

		while (pq.size() > 1) {
			TreeNode left = pq.remove();
			TreeNode right = pq.remove();
			TreeNode parent = new TreeNode('\0',
					left.myWeight + right.myWeight, left, right);
			pq.add(parent);

		}
		return pq.remove();

	}

	public int[] countLetters(InputStream raw) throws IOException {

		int[] result = new int[IHuffConstants.ALPH_SIZE];
		BitInputStream in = new BitInputStream(raw);
		int currentWord = in.readBits(IHuffConstants.BITS_PER_WORD);

		while (currentWord != -1) {
			result[currentWord]++;
			currentWord = in.readBits(IHuffConstants.BITS_PER_WORD);
		}
		return result;
	}

	public void setViewer(HuffViewer viewer) {
		myViewer = viewer;
	}

	public int uncompress(InputStream in, OutputStream out) throws IOException {

		BitInputStream input = new BitInputStream(in);
		int magic = input.readBits(BITS_PER_INT);
		if (magic != MAGIC_NUMBER) {
			throw new IOException("magic number not right");
		}

		int[] checkCount = new int[ALPH_SIZE];
		for (int k = 0; k < ALPH_SIZE; k++) {
			checkCount[k] = input.readBits(BITS_PER_INT);
		}
		for (int k = 0; k < ALPH_SIZE; k++) {
			if (checkCount[k] != 0) {
				System.out.println(k + ":" + checkCount[k]);
			}
		}


		TreeNode root = null;
		PriorityQueue<TreeNode> q = new PriorityQueue<TreeNode>();
		for (int i = 0; i < checkCount.length; i++) {
			if (checkCount[i] != 0) {
				q.add(new TreeNode(i, checkCount[i]));
			}
		}
		q.add(new TreeNode(256, 1));
		while (!q.isEmpty()) {
			if (q.size() == 1) {
				root = q.remove();
				break;
			}
			TreeNode node1 = q.remove();
			TreeNode node2 = q.remove();
			TreeNode newNode = new TreeNode(0, node1.myWeight + node2.myWeight);
			newNode.myLeft = node1;
			newNode.myRight = node2;
			q.add(newNode);
		}


		Map<String, Integer> mapping = new HashMap<String, Integer>();
		recurUnMap(mapping, root, "", 0);
		for (String mvalue : mapping.keySet()) {
			System.out.println((Integer.valueOf(mapping.get(mvalue))) + ":"
					+ mvalue);
		}


		BitOutputStream bitsOut = new BitOutputStream(out);
		String value = "";

		int numb = 0;
		while (true) {
			if (!mapping.containsKey(value)) {
				int bits = input.readBits(1);
				System.out.print(bits);
				if (bits == -1) {
					throw new IOException("error reading bits, no PSEUDO-EOF");
				}
				value += bits;
			} else {
				if (mapping.get(value).equals(PSEUDO_EOF))
					break;
				bitsOut.write(mapping.get(value));
				System.out.print(" ");
				numb += 8;
				value = "";
			}

		}
		bitsOut.close();
		return numb;

	}


	public TreeNode readTree(BitInputStream in) throws IOException{
		if(in.readBits(1)==1){
			int nodeValue=in.readBits(9);
			return new TreeNode(nodeValue, 0, null, null);
		}

		return	new TreeNode(-1,0,readTree(in), readTree(in));


	}


	public void recurUnMap(Map<String, Integer> mapping, TreeNode node,
			String value, int i) {
		if (node.myLeft == null) {
			mapping.put(value, node.myValue);
			return;
		}
		recurUnMap(mapping, node.myLeft, value + "0", i + 1);
		recurUnMap(mapping, node.myRight, value + "1", i + 1);
	}

	private void showString(String s) {
		myViewer.update(s);
	}

}
