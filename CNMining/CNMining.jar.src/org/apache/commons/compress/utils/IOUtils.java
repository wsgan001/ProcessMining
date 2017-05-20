/*    */ package org.apache.commons.compress.utils;
/*    */ 
/*    */ import java.io.IOException;
/*    */ import java.io.InputStream;
/*    */ import java.io.OutputStream;
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ public final class IOUtils
/*    */ {
/*    */   public static long copy(InputStream input, OutputStream output)
/*    */     throws IOException
/*    */   {
/* 47 */     return copy(input, output, 8024);
/*    */   }
/*    */   
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */   public static long copy(InputStream input, OutputStream output, int buffersize)
/*    */     throws IOException
/*    */   {
/* 63 */     byte[] buffer = new byte[buffersize];
/* 64 */     int n = 0;
/* 65 */     long count = 0L;
/* 66 */     while (-1 != (n = input.read(buffer))) {
/* 67 */       output.write(buffer, 0, n);
/* 68 */       count += n;
/*    */     }
/* 70 */     return count;
/*    */   }
/*    */ }


/* Location:              /home/menick/Scrivania/ProcessMining/CNMining/CNMining.jar!/org/apache/commons/compress/utils/IOUtils.class
 * Java compiler version: 4 (48.0)
 * JD-Core Version:       0.7.1
 */