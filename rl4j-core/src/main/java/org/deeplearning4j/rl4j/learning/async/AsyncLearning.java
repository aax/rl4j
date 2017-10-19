package org.deeplearning4j.rl4j.learning.async;

import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.rl4j.learning.Learning;
import org.deeplearning4j.rl4j.network.NeuralNet;
import org.deeplearning4j.rl4j.space.ActionSpace;
import org.deeplearning4j.rl4j.space.Encodable;
import org.nd4j.linalg.factory.Nd4j;

/**
 * @author rubenfiszel (ruben.fiszel@epfl.ch) 7/25/16.
 *
 * Async learning always follow the same pattern in RL4J
 * -launch the Global thread
 * -launch the "save threads"
 * -periodically evaluate the model of the global thread for monitoring purposes
 *
 */
@Slf4j
public abstract class AsyncLearning<O extends Encodable, A, AS extends ActionSpace<A>, NN extends NeuralNet>
                extends Learning<O, A, AS, NN> {


    private Thread[] threads;

    public AsyncLearning(AsyncConfiguration conf) {
        super(conf);
    }

    public abstract AsyncConfiguration getConfiguration();

    protected abstract AsyncThread newThread(int i);

    protected abstract AsyncGlobal<NN> getAsyncGlobal();

    protected void startGlobalThread() {
        getAsyncGlobal().start();
    }

    protected boolean isTrainingComplete() {
        return getAsyncGlobal().isTrainingComplete();
    }

    public void launchThreads() {
        startGlobalThread();
        this.threads = new Thread[getConfiguration().getNumThread()];
        for (int i = 0; i < getConfiguration().getNumThread(); i++) {
            Thread t = newThread(i);
            threads[i] = t;
            Nd4j.getAffinityManager().attachThreadToDevice(t,
                            i % Nd4j.getAffinityManager().getNumberOfDevices());
            t.start();
        }
        log.info("Threads launched.");
    }

    @Override
    public int getStepCounter() {
        return getAsyncGlobal().getT().get();
    }

    public void train() {

        try {
            log.info("AsyncLearning training starting.");
            launchThreads();

            //this is simply for stat purposes
            getDataManager().writeInfo(this);
            synchronized (this) {
                while (!isTrainingComplete() && getAsyncGlobal().isRunning()) {
//                    getPolicy().play(getMdp(), getHistoryProcessor());
//                    getDataManager().writeInfo(this);
                    wait(2000);
                }
            }
            for (Thread thread : threads) {
                thread.interrupt();
            }
        } catch (Exception e) {
            log.error("Training failed.", e);
        }
    }


}
