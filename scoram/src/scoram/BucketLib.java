package scoram;

import circuits.IntegerLib;
import flexsc.CompEnv;



public class BucketLib<T> extends IntegerLib<T> {
	public Block<T> dummyBlock;
	protected int lengthOfIden;
	protected int lengthOfPos;
	protected int lengthOfData;
	public BucketLib(int lengthOfIden, int lengthOfPos, int lengthOfData, CompEnv<T> e) {
		super(e);
		this.lengthOfData = lengthOfData;
		this.lengthOfIden = lengthOfIden;
		this.lengthOfPos = lengthOfPos;
		dummyBlock = new Block<T>(zeros(lengthOfIden), zeros(lengthOfPos), zeros(lengthOfData), SIGNAL_ONE);
	}
	
	public T isFull(Block<T>[] bucket) throws Exception
	{
		T res = SIGNAL_ONE;
		for(int i = 0; i < bucket.length; ++i) {
			res = and(res, not(bucket[i].isDummy));
		}
		return res;
	}

	public T isEmpty(Block<T>[] bucket) throws Exception
	{
		T res = SIGNAL_ONE;
		for(int i = 0; i < bucket.length; ++i) {
			res = and(res, bucket[i].isDummy);
		}
		return res;
	}
	
	public Block<T> conditionalReadAndRemove(Block<T>[] bucket, T[] iden, T condition) throws Exception {
		Block<T> result = dummyBlock;
		for(int i = 0; i < bucket.length; ++i) {
			T match = eq(iden, bucket[i].iden);
			match = and(match, not(bucket[i].isDummy));
			match = and(match, condition);
			result = mux(result, bucket[i], match);
			bucket[i].isDummy = mux(bucket[i].isDummy, SIGNAL_ONE, match);
		}
		return result;
	}

	public Block<T> readAndRemove(Block<T>[] bucket, T[] iden) throws Exception {
		Block<T> result = dummyBlock;
		for(int i = 0; i < bucket.length; ++i) {
			T match = eq(iden, bucket[i].iden);
			match = and(match, not(bucket[i].isDummy));
			result = mux(result, bucket[i], match);
			bucket[i].isDummy = mux(bucket[i].isDummy, SIGNAL_ONE, match);
		}
		return result;
	}

	public Block<T> conditionalReadAndRemove(Block<T>[][] blocks, T[] iden, T condition) throws Exception {
		Block<T>[] res = newBlockArray(blocks.length);
		for(int i = 0; i < blocks.length; ++i)
			res[i] = conditionalReadAndRemove(blocks[i], iden, condition);
		return conditionalReadAndRemove(res, iden, condition);		
	}
	
	public Block<T> readAndRemove(Block<T>[][] blocks, T[] iden) throws Exception {
		Block<T>[] res = newBlockArray(blocks.length);
		for(int i = 0; i < blocks.length; ++i)
			res[i] = readAndRemove(blocks[i], iden);
		return readAndRemove(res, iden);
		
		/*
		RDRUnitGadget gadget = new RDRUnitGadget(env, "localhost", 11345);
		
		Object[] input = new Object[] { 
//				new Object[]{Arrays.copyOfRange(blocks, 0, blocks.length/2), iden} ,
//				new Object[]{Arrays.copyOfRange(blocks, blocks.length/2, blocks.length) , iden}
				new Object[]{Arrays.copyOfRange(blocks, 0, blocks.length/3), iden} ,
				new Object[]{Arrays.copyOfRange(blocks, blocks.length/3, blocks.length*2/3), iden} ,
				new Object[]{Arrays.copyOfRange(blocks, blocks.length*2/3, blocks.length), iden} ,
		};
		Object[] result = gadget.runGadget(gadget, input, env);
		Block<T>[] resultbuckets = new Block[result.length];
		for(int i = 0; i < result.length; ++i)
			resultbuckets[i]= (Block<T>) result[i];
		return readAndRemove(resultbuckets, iden);
	*/}


	/*private Block<T> readAndRemoveInternal(Block<T>[][] blocks, T[] iden) throws Exception {
		Block<T>[] res = newBlockArray(blocks.length);
		for(int i = 0; i < blocks.length; ++i)
			res[i] = readAndRemove(blocks[i], iden);
		return readAndRemove(res, iden);	
}
	
	class RDRUnitGadget extends Gadget<T> {
		private RDRUnitGadget(CompEnv<T> e, String host, int port, Object[] input) {
			super(e, host, port, input);
		}
		public RDRUnitGadget(CompEnv<T> e, String host, int port) {
			super(e, host, port);
		}
		@Override
		public Object secureCompute(CompEnv<T> e, Object[] o) throws Exception {
			Block<T>[][] signala = (Block<T>[][]) o[0];
			T[] iden = (T[]) o[1];
			BucketLib<T> lib = new BucketLib<T>(lengthOfIden, lengthOfPos, lengthOfData, e);
			return lib.readAndRemoveInternal(signala, iden);
		}
		
	     protected RDRUnitGadget getGadget(CompEnv<T>e , String host, int port, Object[] inputs2) {
			return new RDRUnitGadget(e, host, port, inputs2);	
		}
	};
*/
	public void conditionalAdd(Block<T>[] bucket, Block<T> newBlock, T condition) throws Exception {
		T added = not(condition);
		for(int i = 0; i < bucket.length; ++i) {
			T match = and( not(bucket[i].isDummy), eq(newBlock.iden, bucket[i].iden) );
			added = or(match, added);
		}
		for(int i = 0; i < bucket.length; ++i) {
			T match = bucket[i].isDummy;
			T shouldAdd = and(not(added), match);
			added = or(added, shouldAdd);
			bucket[i] = mux(bucket[i], newBlock, shouldAdd);
		}
	}

	public void add(Block<T>[] bucket, Block<T> newBlock) throws Exception {
		T added = SIGNAL_ZERO;
		for(int i = 0; i < bucket.length; ++i) {
			T match = and( not(bucket[i].isDummy), eq(newBlock.iden, bucket[i].iden) );
			added = or(match, added);
		}
		for(int i = 0; i < bucket.length; ++i) {
			T match = bucket[i].isDummy;
			T shouldAdd = and(not(added), match);
			added = or(added, shouldAdd);
			bucket[i] = mux(bucket[i], newBlock, shouldAdd);
		}
		

	}

	public Block<T> pop(Block<T>[] bucket) throws Exception {
		Block<T> result = dummyBlock;
		T poped = SIGNAL_ZERO;// condition=T => shouldpop => set to poped;
		for(int i = 0; i < bucket.length; ++i) {
			T notDummy = not(bucket[i].isDummy);
			T shouldPop = and(not(poped), notDummy);
			poped = or(poped, shouldPop);
			result = mux(result, bucket[i], shouldPop);

			bucket[i].isDummy = mux(bucket[i].isDummy, SIGNAL_ONE, shouldPop);
		}
		return result;
	}

	public Block<T> conditionalPop(Block<T>[] bucket, T condition) throws Exception {
		Block<T> result = dummyBlock;
		T poped = not(condition);// condition=T => shouldpop => set to poped;
		for(int i = 0; i < bucket.length; ++i) {
			T notDummy = not(bucket[i].isDummy);
			T shouldPop = and(not(poped), notDummy);
			poped = or(poped, shouldPop);
			result = mux(result, bucket[i], shouldPop);

			bucket[i].isDummy = mux(bucket[i].isDummy, SIGNAL_ONE, shouldPop);
		}
		return result;
	}

	public Block<T> mux(Block<T> a, Block<T> b, T choose) throws Exception {
		T[] iden = mux(a.iden, b.iden, choose);
		T[] pos = mux(a.pos, b.pos, choose);
		T[] data = mux(a.data, b.data, choose);
		T isDummy = mux(a.isDummy, b.isDummy, choose);
		return new Block<T>(iden, pos, data, isDummy);
	}

	public Block<T> xor(Block<T> a, Block<T> b) {
		T[] iden = xor(a.iden, b.iden);
		T[] pos = xor(a.pos, b.pos);
		T[] data = xor(a.data, b.data);
		T isDummy = xor(a.isDummy, b.isDummy);
		return new Block<T>(iden, pos, data, isDummy);
	}
	
	public Block<T>[] xor(Block<T>[] a, Block<T>[] b) {
		assert(a.length == b.length) : "xor Block<T>s error";
		Block<T>[] result = newBlockArray(b.length);
		for(int i = 0; i < a.length; ++i)
			result[i] = xor(a[i], b[i]);
		return result;
	}

	public Block<T>[][] xor(Block<T>[][] a, Block<T>[][] b) {
		assert(a.length == b.length) : "xor Block<T>s error";
		Block<T>[][] result = newBlockMatrix(a.length);
		for(int i = 0; i < a.length; ++i){
			result[i] = newBlockArray(a[i].length);
			for(int j = 0; j < a[i].length; ++j)
				result[i][j] = xor(a[i][j], b[i][j]);
			}
		return result;
	}
	
	public Block<T> copy(Block<T> b) {
//		return new Block<T>(copy(b.iden), copy(b.pos), copy(b.data), b.isDummy);
		return new Block<T>(b.iden, b.pos, b.data, b.isDummy);
	}
	
	@SuppressWarnings("unchecked")
	public Block<T>[] newBlockArray(int len) {
		return new Block[len];
	}
	
	@SuppressWarnings("unchecked")
	public Block<T>[][] newBlockMatrix(int x, int y) {
		return new Block[x][y];
	}
	
	@SuppressWarnings("unchecked")
	public Block<T>[][] newBlockMatrix(int x) {
		return new Block[x][];
	}
}
