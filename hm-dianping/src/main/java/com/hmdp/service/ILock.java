package com.hmdp.service;

/**
 * @author haowe
 */
public interface ILock {



    boolean tryLock(long timeoutSec);



    void unlock();
}
