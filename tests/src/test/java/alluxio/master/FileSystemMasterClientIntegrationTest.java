/*
 * The Alluxio Open Foundation licenses this work under the Apache License, version 2.0
 * (the "License"). You may not use this work except in compliance with the License, which is
 * available at www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied, as more fully set forth in the License.
 *
 * See the NOTICE file distributed with this work for information regarding copyright ownership.
 */

package alluxio.master;

import alluxio.AlluxioURI;
import alluxio.LocalAlluxioClusterResource;
import alluxio.PropertyKey;
import alluxio.BaseIntegrationTest;
import alluxio.client.file.FileSystem;
import alluxio.client.file.FileSystemMasterClient;
import alluxio.client.file.options.CreateFileOptions;
import alluxio.client.file.options.GetStatusOptions;
import alluxio.exception.AlluxioException;
import alluxio.exception.status.NotFoundException;

import com.google.common.base.Throwables;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

/**
 * Tests the internal implementation of alluxio Master via a {@link FileSystemMasterClient}.
 */
public final class FileSystemMasterClientIntegrationTest extends BaseIntegrationTest {
  private static final GetStatusOptions GET_STATUS_OPTIONS = GetStatusOptions.defaults();

  @Rule
  public LocalAlluxioClusterResource mLocalAlluxioClusterResource =
      new LocalAlluxioClusterResource.Builder()
          .setProperty(PropertyKey.USER_RPC_RETRY_MAX_NUM_RETRY, 30).build();

  @Test
  public void openClose() throws AlluxioException, IOException {
    FileSystemMasterClient fsMasterClient =
        FileSystemMasterClient.Factory.create(MasterClientConfig.defaults());
    AlluxioURI file = new AlluxioURI("/file");
    Assert.assertFalse(fsMasterClient.isConnected());
    fsMasterClient.connect();
    Assert.assertTrue(fsMasterClient.isConnected());
    fsMasterClient.createFile(file, CreateFileOptions.defaults());
    Assert.assertNotNull(fsMasterClient.getStatus(file, GET_STATUS_OPTIONS));
    fsMasterClient.disconnect();
    Assert.assertFalse(fsMasterClient.isConnected());
    fsMasterClient.connect();
    Assert.assertTrue(fsMasterClient.isConnected());
    Assert.assertNotNull(fsMasterClient.getStatus(file, GET_STATUS_OPTIONS));
    fsMasterClient.close();
  }

  @Test(timeout = 3000, expected = NotFoundException.class)
  public void getFileInfoReturnsOnError() throws Exception {
    // This test was created to show that an infinite loop occurs.
    // The timeout will protect against this, and the change was to throw a IOException
    // in the cases we don't want to disconnect from master
    FileSystemMasterClient fsMasterClient =
        FileSystemMasterClient.Factory.create(MasterClientConfig.defaults());
    fsMasterClient.getStatus(new AlluxioURI("/doesNotExist"), GET_STATUS_OPTIONS);
    fsMasterClient.close();
  }

  @Test(timeout = 300000)
  public void masterUnavailable() throws Exception {
    FileSystem fileSystem = mLocalAlluxioClusterResource.get().getClient();
    mLocalAlluxioClusterResource.get().getLocalAlluxioMaster().stop();

    Thread thread = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          Thread.sleep(3000);
          mLocalAlluxioClusterResource.get().getLocalAlluxioMaster().start();
        } catch (InterruptedException e) {
          throw Throwables.propagate(e);
        }
      }
    });
    thread.start();

    fileSystem.listStatus(new AlluxioURI("/"));
    thread.join();
  }
}
