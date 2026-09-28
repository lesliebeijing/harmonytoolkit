package com.lesliefang.harmonytoolkit.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ClassifyFileTest {

    @Test
    fun specialInfoFilesAreJson() {
        assertEquals(ViewerKind.JSON, classifyFile("pack.info").viewer)
        assertEquals(ViewerKind.UNKNOWN, classifyFile(".pages.info").viewer)
        assertEquals(ViewerKind.JSON, classifyFile("module.json").viewer)
        assertEquals(ViewerKind.JSON, classifyFile("sourceMaps.map").viewer)
    }

    @Test
    fun declarationAndJson5AreText() {
        assertEquals(ViewerKind.TEXT, classifyFile("ohos.d.ts").viewer)
        assertEquals(ViewerKind.TEXT, classifyFile("api.d.ets").viewer)
        assertEquals(ViewerKind.TEXT, classifyFile("build-profile.json5").viewer)
    }

    @Test
    fun regularRoutingUnchanged() {
        assertEquals(ViewerKind.RESOURCES_INDEX, classifyFile("resources.index").viewer)
        assertEquals(ViewerKind.IMAGE, classifyFile("startIcon.png").viewer)
        assertEquals(ViewerKind.ABC, classifyFile("modules.abc").viewer)
        assertEquals(ViewerKind.BINARY, classifyFile("libhilog.so").viewer)
        assertEquals(ViewerKind.TEXT, classifyFile("readme.txt").viewer)
        assertEquals(ViewerKind.UNKNOWN, classifyFile("logo.svg").viewer)
    }
}
