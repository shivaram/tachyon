/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tachyon.examples;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.apache.thrift.TException; 
import org.apache.log4j.Logger;

import tachyon.CommonUtils;
import tachyon.Constants;
import tachyon.Version;
import tachyon.client.OutStream;
import tachyon.client.TachyonByteBuffer;
import tachyon.client.TachyonFS;
import tachyon.client.TachyonFile;
import tachyon.client.WriteType;
import tachyon.client.table.RawColumn;
import tachyon.client.table.RawTable;
import tachyon.thrift.FileAlreadyExistException;
import tachyon.thrift.InvalidPathException;
import tachyon.thrift.OutOfMemoryForPinFileException;
import tachyon.thrift.TableColumnException;
import tachyon.thrift.TableDoesNotExistException;

public class BasicRawTableOperations {
  private static Logger LOG = Logger.getLogger(Constants.LOGGER_TYPE);

  private static final int COLS = 3;
  private static TachyonFS sTachyonClient;
  private static String sTablePath = null;
  private static int mId;
  private static WriteType sWriteType = null;

  public static void createRawTable() throws IOException {
    long startTimeMs = CommonUtils.getCurrentMs();
    ByteBuffer data = ByteBuffer.allocate(12);
    data.order(ByteOrder.nativeOrder());
    data.putInt(-1);
    data.putInt(-2);
    data.putInt(-3);
    data.flip();
    mId = sTachyonClient.createRawTable(sTablePath, 3, data);
    CommonUtils.printTimeTakenMs(startTimeMs, LOG, "createRawTable with id " + mId);
  }

  public static void writeParition() 
      throws IOException, TableDoesNotExistException, InvalidPathException, 
      FileAlreadyExistException, TException {
    RawTable rawTable = sTachyonClient.getRawTable(sTablePath);

    LOG.info("Writing data...");
    for (int column = 0; column < COLS; column ++) {
      RawColumn rawColumn = rawTable.getRawColumn(column);
      if (!rawColumn.createPartition(0)) {
        CommonUtils.runtimeException("Failed to create partition in table " + sTablePath + 
            " under column " + column);
      }

      ByteBuffer buf = ByteBuffer.allocate(80);
      buf.order(ByteOrder.nativeOrder());
      for (int k = 0; k < 20; k ++) {
        buf.putInt(k);
      }
      buf.flip();
      CommonUtils.printByteBuffer(LOG, buf);
      buf.flip();

      TachyonFile tFile = rawColumn.getPartition(0);
      OutStream os = tFile.getOutStream(sWriteType);
      os.write(buf.array());
      os.close();
    }
  }

  public static void readPartition()
      throws IOException, TableDoesNotExistException, InvalidPathException, TException {
    LOG.info("Reading data...");
    RawTable rawTable = sTachyonClient.getRawTable(mId);
    ByteBuffer metadata = rawTable.getMetadata();
    LOG.info("Metadata: ");
    metadata.order(ByteOrder.nativeOrder());
    LOG.info(metadata.getInt() + " ");
    LOG.info(metadata.getInt() + " ");
    LOG.info(metadata.getInt() + " ");

    for (int column = 0; column < COLS; column ++) {
      RawColumn rawColumn = rawTable.getRawColumn(column);
      TachyonFile tFile = rawColumn.getPartition(0);

      TachyonByteBuffer buf = tFile.readByteBuffer();
      if (buf == null) {
        tFile.recache();
        buf = tFile.readByteBuffer();
      }
      buf.DATA.order(ByteOrder.nativeOrder());
      CommonUtils.printByteBuffer(LOG, buf.DATA);
      buf.close();
    }
  }

  public static void main(String[] args)
      throws IOException, TableDoesNotExistException, OutOfMemoryForPinFileException, 
      InvalidPathException, FileAlreadyExistException, TableColumnException, TException {
    if (args.length != 3) {
      System.out.println("java -cp target/tachyon-" + Version.VERSION + 
          "-jar-with-dependencies.jar " +
          "tachyon.examples.BasicRawTableOperations <TachyonMasterAddress> <FilePath>");
      System.exit(-1);
    }
    sTachyonClient = TachyonFS.get(args[0]);
    sTablePath = args[1];
    sWriteType = WriteType.getOpType(args[2]);
    createRawTable();
    writeParition();
    readPartition();
    System.exit(0);
  }
}