/*    */ package org.apache.commons.compress.archivers;
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
/*    */ public class ArchiveException
/*    */   extends Exception
/*    */ {
/*    */   private static final long serialVersionUID = 2772690708123267100L;
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
/*    */   public ArchiveException(String message)
/*    */   {
/* 37 */     super(message);
/*    */   }
/*    */   
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */   public ArchiveException(String message, Exception cause)
/*    */   {
/* 49 */     super(message);
/* 50 */     initCause(cause);
/*    */   }
/*    */ }


/* Location:              /home/menick/Scrivania/ProcessMining/CNMining/CNMining.jar!/org/apache/commons/compress/archivers/ArchiveException.class
 * Java compiler version: 4 (48.0)
 * JD-Core Version:       0.7.1
 */