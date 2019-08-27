package org.xbo.core.services.ratelimiter.adapter;

import org.xbo.core.services.ratelimiter.RuntimeData;

public interface IRateLimiter {

  boolean acquire(RuntimeData data);

}
