package scoram;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;

import flexsc.CompEnv;
import flexsc.Mode;
import flexsc.Party;

public class RecursiveSCORAM<T> {
	public TrivialPrivateOram<T> baseOram;
	public ArrayList<SCORAM<T>> clients = new ArrayList<>();
	int initialLengthOfIden;
	int recurFactor;
	int cutoff;
	int capacity;
	public int lengthOfIden;
	SecureRandom rng = new SecureRandom();
	protected InputStream is;
	protected OutputStream os;
	Party p;
	public RecursiveSCORAM(CompEnv<T> env, int N, int dataSize, int cutoff, int recurFactor, 
			int capacity,int sp) throws Exception {
		init(env, N, dataSize, cutoff, recurFactor, capacity, sp);
	}
	
	//with default params
	public RecursiveSCORAM(CompEnv<T> env, int N, int dataSize) throws Exception {
		init(env, N, dataSize, 1024, 8, 6, 80);
	}
	
	//with default params
	// for compatibility with other code, should not be used
	@SuppressWarnings({ "unchecked" })
	public RecursiveSCORAM(InputStream is, OutputStream os, int N, int dataSize, Party p) throws Exception {
		init(CompEnv.getEnv(Mode.REAL, p, is, os), N, dataSize, 1024, 8, 6, 80);
	}
	
	void init(CompEnv<T> env, int N, int dataSize, int cutoff, int recurFactor, 
			int capacity,int sp) throws Exception {
		this.is = env.is;
		this.os = env.os;
		this.p = env.p;
		this.cutoff = cutoff;
		this.recurFactor = recurFactor;
		this.capacity = capacity;
		SCORAM<T>  oram = new SCORAM<T>(env, N, dataSize, capacity, sp);
		clients.add(oram);
		int newDataSize = oram.lengthOfPos * recurFactor, newN = (1<<oram.lengthOfIden)/recurFactor;
		while(newN > cutoff) {
			oram = new SCORAM<T>(env, newN, newDataSize, capacity, sp);
			clients.add(oram);
			newDataSize = oram.lengthOfPos * recurFactor;
			newN = (1<<oram.lengthOfIden)  / recurFactor;
		}
		SCORAM<T> last = clients.get(clients.size()-1);
		baseOram = new TrivialPrivateOram<T>(env, (1<<last.lengthOfIden), last.lengthOfPos);
		lengthOfIden = clients.get(0).lengthOfIden;
	}
	
	public T[] read(T[] iden) throws Exception {
		T[][] poses = travelToDeep(iden, 1);
		SCORAM<T> currentOram = clients.get(0);

		boolean[] oldPos = baseOram.env.outputToAlice(poses[0]);
		oldPos = baseOram.lib.syncBooleans(oldPos);
		T[] res = currentOram.read(iden, oldPos, poses[1]);
		return res;
	}
	
	public void write(T[] iden, T[] data) throws Exception {
		T[][] poses = travelToDeep(iden, 1);
		SCORAM<T> currentOram = clients.get(0);

		boolean[] oldPos = baseOram.env.outputToAlice(poses[0]);
		oldPos = baseOram.lib.syncBooleans(oldPos);
		currentOram.write(iden, oldPos, poses[1], data);
	}
	
	public T[][] travelToDeep(T[] iden, int level) throws Exception {
		if(level == clients.size()) {
			T[] baseMap = baseOram.readAndRemove(subIdentifier(iden, baseOram));
			T[] ithPos = baseOram.lib.rightPublicShift(iden, baseOram.lengthOfIden);//iden>>baseOram.lengthOfIden;
			
			T[] pos = extract(baseMap, ithPos, clients.get(level-1).lengthOfPos);
			
			T[] newPos = baseOram.lib.randBools(rng, clients.get(level-1).lengthOfPos);
			put(baseMap, ithPos, newPos);
			baseOram.putBack(subIdentifier(iden, baseOram), baseMap);
			os.flush();
			T[][] result = baseOram.env.newTArray(2, 0);
			result[0] = pos;
			result[1] = newPos;
			return result;
		}
		else {
			SCORAM<T> currentOram = clients.get(level);

			T[][] poses = travelToDeep(subIdentifier(iden, currentOram), level+1);
			boolean[] oldPos = clients.get(clients.size()-1).env.outputToAlice(poses[0]);
			oldPos = baseOram.lib.syncBooleans(oldPos);

			T[] data = currentOram.readAndRemove(subIdentifier(iden, currentOram), oldPos, true);
			T[] ithPos = currentOram.lib.rightPublicShift(iden, currentOram.lengthOfIden);//iden>>currentOram.lengthOfIden;//iden/(1<<currentOram.lengthOfIden);

			T[] pos = extract(data, ithPos, clients.get(level-1).lengthOfPos);
			T[] tmpNewPos = baseOram.lib.randBools(rng, clients.get(level-1).lengthOfPos);
			put(data, ithPos, tmpNewPos);
			currentOram.putBack(subIdentifier(iden, currentOram), poses[1], data);
			T[][] result = currentOram.env.newTArray(2, 0);
			result[0] = pos;
			result[1] = tmpNewPos;
			return result;
		}
	}
	
	public T[] subIdentifier(T[] iden, OramParty<T> o) {
		return o.lib.padSignal(iden, o.lengthOfIden);
	}
	
	public T[] extract(T[] array, T[] ithPos, int length) throws Exception {
		int numberOfEntry = array.length/length;
		T[] result = Arrays.copyOfRange(array, 0, length);
		for(int i = 1; i < numberOfEntry; ++i) {
			T hit = baseOram.lib.eq(baseOram.lib.toSignals(i, ithPos.length), ithPos);
			result = baseOram.lib.mux(result, Arrays.copyOfRange(array, i*length, (i+1)*length), hit);
		}
		return result;
	}
	
	public void put(T[] array, T[] ithPos, T[] content) throws Exception {
		int numberOfEntry = array.length/content.length;
		for(int i = 0; i < numberOfEntry; ++i) {
			T hit = baseOram.lib.eq(baseOram.lib.toSignals(i, ithPos.length), ithPos);
			T[] tmp = baseOram.lib.mux(Arrays.copyOfRange(array, i*content.length, (i+1)*content.length), content, hit);
			System.arraycopy(tmp, 0, array, i*content.length, content.length);
		}
	}

}