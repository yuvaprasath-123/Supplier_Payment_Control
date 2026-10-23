package com.ofss.internal.verification;

/** Always attempt every shutdown; one failed close must not strand another receiver/client. */
public final class VerificationShutdown {
    private VerificationShutdown() {}
    public static boolean closeAll(AutoCloseable... resources){
        boolean stopped=true;
        for(var resource:resources)if(resource!=null)try{resource.close();}catch(Throwable failure){stopped=false;}
        return stopped;
    }
}
