package com.today.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.today.identity.IdentityService;
import com.today.punch.PunchMapper;
import java.nio.file.Files;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import org.springframework.http.HttpHeaders;
import org.junit.jupiter.api.Test;

class MediaControllerTest {
  @TempDir Path temp;

  @Test
  void orphanedPhotoIsNotAccessibleEvenIfFileStillExists() {
    LocalMediaStorage storage = mock(LocalMediaStorage.class);
    IdentityService identity = mock(IdentityService.class);
    PunchMapper punches = mock(PunchMapper.class);
    when(identity.getCurrentUserId()).thenReturn("u1");
    MediaController controller = new MediaController(storage, identity, punches);

    assertEquals(404, controller.getMedia("/punch/u1/removed.jpg").getStatusCode().value());
    verify(punches).isPhotoReferenced("punch/u1/removed.jpg", "u1");
    verifyNoInteractions(storage);
    assertEquals(404, controller.getMedia("/punch/u2/active.jpg").getStatusCode().value());
    verifyNoMoreInteractions(punches);
  }

  @Test
  void activePhotoIsNotCachedAfterAccessIsRevoked() throws Exception {
    LocalMediaStorage storage = mock(LocalMediaStorage.class);
    IdentityService identity = mock(IdentityService.class);
    PunchMapper punches = mock(PunchMapper.class);
    when(identity.getCurrentUserId()).thenReturn("u1");
    when(punches.isPhotoReferenced("punch/u1/active.jpg", "u1")).thenReturn(true);
    Path photo = temp.resolve("active.jpg");
    Files.write(photo, new byte[] {1, 2, 3});
    when(storage.resolveReadable("punch/u1/active.jpg")).thenReturn(photo);

    var response = new MediaController(storage, identity, punches).getMedia("/punch/u1/active.jpg");
    assertEquals(200, response.getStatusCode().value());
    assertEquals("private, no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
  }
}
