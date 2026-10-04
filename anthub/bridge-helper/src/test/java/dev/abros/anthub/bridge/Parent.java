package dev.abros.anthub.bridge;
public final class Parent {public static void main(String[] args)throws Exception{System.out.println(ProcessHandle.current().info().startInstant().orElseThrow());System.out.flush();System.in.read();}}
