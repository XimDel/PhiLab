package com.example.philab.data.aruco

/**
 * ArUco DICT_4X4_50 dictionary.
 *
 * Bit patterns extracted directly from OpenCV via:
 *   cv2.aruco.generateImageMarker(DICT_4X4_50, id, 6)
 *
 * Each marker is a 4×4 grid: 0 = white cell, 1 = black cell.
 * The full printed marker adds a 1-cell black border, making it 6×6.
 */
object ArucoDictionary {

    /**
     * Returns the 4×4 bit matrix for marker [id] (0–49).
     * Row-major: bits[row][col].
     */
    fun getBits(id: Int): Array<IntArray> {
        require(id in 0..49) { "Marker ID must be 0–49 for DICT_4X4_50" }
        return DICT_4X4_50[id]
    }

    private val DICT_4X4_50: Array<Array<IntArray>> = arrayOf(
        // ID 0
        arrayOf(
            intArrayOf(0, 1, 0, 0),
            intArrayOf(1, 0, 1, 0),
            intArrayOf(1, 1, 0, 0),
            intArrayOf(1, 1, 0, 1)
        ),
        // ID 1
        arrayOf(
            intArrayOf(1, 1, 1, 1),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 1, 1, 0),
            intArrayOf(0, 1, 0, 1)
        ),
        // ID 2
        arrayOf(
            intArrayOf(1, 1, 0, 0),
            intArrayOf(1, 1, 0, 0),
            intArrayOf(1, 1, 0, 1),
            intArrayOf(0, 0, 1, 0)
        ),
        // ID 3
        arrayOf(
            intArrayOf(0, 1, 1, 0),
            intArrayOf(0, 1, 1, 0),
            intArrayOf(1, 0, 1, 1),
            intArrayOf(1, 0, 0, 1)
        ),
        // ID 4
        arrayOf(
            intArrayOf(1, 0, 1, 0),
            intArrayOf(1, 0, 1, 1),
            intArrayOf(0, 1, 1, 0),
            intArrayOf(0, 0, 0, 1)
        ),
        // ID 5
        arrayOf(
            intArrayOf(1, 0, 0, 0),
            intArrayOf(0, 1, 1, 0),
            intArrayOf(0, 0, 1, 1),
            intArrayOf(0, 0, 1, 0)
        ),
        // ID 6
        arrayOf(
            intArrayOf(0, 1, 1, 0),
            intArrayOf(0, 0, 0, 1),
            intArrayOf(1, 1, 0, 1),
            intArrayOf(0, 0, 0, 1)
        ),
        // ID 7
        arrayOf(
            intArrayOf(0, 0, 1, 1),
            intArrayOf(1, 0, 1, 1),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(1, 1, 0, 1)
        ),
        // ID 8
        arrayOf(
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 1),
            intArrayOf(0, 0, 1, 0),
            intArrayOf(0, 1, 0, 1)
        ),
        // ID 9
        arrayOf(
            intArrayOf(0, 0, 1, 1),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(1, 0, 1, 0),
            intArrayOf(1, 0, 0, 1)
        ),
        // ID 10
        arrayOf(
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 1, 1, 0),
            intArrayOf(0, 1, 1, 0),
            intArrayOf(1, 1, 1, 0)
        ),
        // ID 11
        arrayOf(
            intArrayOf(1, 1, 1, 0),
            intArrayOf(1, 1, 1, 0),
            intArrayOf(0, 1, 0, 1),
            intArrayOf(1, 0, 0, 0)
        ),
        // ID 12
        arrayOf(
            intArrayOf(1, 1, 1, 1),
            intArrayOf(0, 0, 0, 1),
            intArrayOf(0, 1, 0, 0),
            intArrayOf(1, 0, 0, 0)
        ),
        // ID 13
        arrayOf(
            intArrayOf(1, 1, 0, 1),
            intArrayOf(0, 1, 0, 1),
            intArrayOf(1, 1, 1, 1),
            intArrayOf(0, 0, 0, 0)
        ),
        // ID 14
        arrayOf(
            intArrayOf(1, 1, 0, 1),
            intArrayOf(1, 0, 1, 1),
            intArrayOf(0, 1, 0, 0),
            intArrayOf(1, 1, 1, 0)
        ),
        // ID 15
        arrayOf(
            intArrayOf(1, 1, 0, 1),
            intArrayOf(1, 0, 0, 1),
            intArrayOf(1, 1, 0, 0),
            intArrayOf(0, 0, 0, 1)
        ),
        // ID 16
        arrayOf(
            intArrayOf(1, 0, 1, 1),
            intArrayOf(1, 0, 0, 1),
            intArrayOf(1, 0, 0, 1),
            intArrayOf(1, 0, 1, 0)
        ),
        // ID 17
        arrayOf(
            intArrayOf(1, 0, 0, 1),
            intArrayOf(1, 0, 0, 1),
            intArrayOf(1, 1, 1, 1),
            intArrayOf(1, 1, 1, 1)
        ),
        // ID 18
        arrayOf(
            intArrayOf(1, 0, 0, 1),
            intArrayOf(0, 0, 1, 1),
            intArrayOf(1, 0, 1, 0),
            intArrayOf(0, 0, 0, 1)
        ),
        // ID 19
        arrayOf(
            intArrayOf(1, 0, 0, 0),
            intArrayOf(1, 0, 0, 1),
            intArrayOf(0, 1, 0, 1),
            intArrayOf(0, 0, 0, 0)
        ),
        // ID 20
        arrayOf(
            intArrayOf(0, 1, 1, 1),
            intArrayOf(1, 0, 0, 1),
            intArrayOf(0, 1, 1, 1),
            intArrayOf(0, 1, 0, 0)
        ),
        // ID 21
        arrayOf(
            intArrayOf(0, 1, 0, 0),
            intArrayOf(1, 1, 1, 1),
            intArrayOf(1, 1, 0, 1),
            intArrayOf(0, 1, 0, 0)
        ),
        // ID 22
        arrayOf(
            intArrayOf(0, 0, 1, 1),
            intArrayOf(0, 0, 1, 1),
            intArrayOf(0, 0, 1, 0),
            intArrayOf(1, 0, 1, 0)
        ),
        // ID 23
        arrayOf(
            intArrayOf(0, 0, 1, 0),
            intArrayOf(0, 0, 1, 0),
            intArrayOf(0, 1, 1, 1),
            intArrayOf(1, 1, 0, 1)
        ),
        // ID 24
        arrayOf(
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 1),
            intArrayOf(1, 0, 1, 1),
            intArrayOf(1, 0, 0, 0)
        ),
        // ID 25
        arrayOf(
            intArrayOf(0, 1, 1, 0),
            intArrayOf(1, 0, 1, 1),
            intArrayOf(1, 0, 0, 0),
            intArrayOf(1, 1, 1, 0)
        ),
        // ID 26
        arrayOf(
            intArrayOf(0, 1, 0, 1),
            intArrayOf(0, 0, 1, 1),
            intArrayOf(0, 0, 0, 1),
            intArrayOf(1, 0, 1, 1)
        ),
        // ID 27
        arrayOf(
            intArrayOf(0, 1, 0, 1),
            intArrayOf(1, 0, 1, 0),
            intArrayOf(1, 0, 1, 0),
            intArrayOf(1, 0, 1, 1)
        ),
        // ID 28
        arrayOf(
            intArrayOf(1, 1, 0, 1),
            intArrayOf(1, 1, 1, 0),
            intArrayOf(1, 1, 0, 1),
            intArrayOf(1, 1, 0, 0)
        ),
        // ID 29
        arrayOf(
            intArrayOf(1, 1, 0, 0),
            intArrayOf(1, 0, 1, 1),
            intArrayOf(1, 0, 0, 1),
            intArrayOf(0, 0, 0, 0)
        ),
        // ID 30
        arrayOf(
            intArrayOf(1, 0, 1, 1),
            intArrayOf(1, 0, 1, 1),
            intArrayOf(1, 1, 1, 0),
            intArrayOf(1, 0, 1, 0)
        ),
        // ID 31
        arrayOf(
            intArrayOf(1, 0, 1, 0),
            intArrayOf(1, 0, 0, 0),
            intArrayOf(0, 1, 0, 0),
            intArrayOf(1, 1, 0, 1)
        ),
        // ID 32
        arrayOf(
            intArrayOf(0, 1, 1, 0),
            intArrayOf(0, 0, 0, 1),
            intArrayOf(0, 0, 1, 1),
            intArrayOf(0, 0, 0, 0)
        ),
        // ID 33
        arrayOf(
            intArrayOf(0, 0, 0, 0),
            intArrayOf(1, 1, 1, 1),
            intArrayOf(0, 0, 1, 1),
            intArrayOf(0, 1, 0, 0)
        ),
        // ID 34
        arrayOf(
            intArrayOf(1, 1, 1, 1),
            intArrayOf(0, 1, 1, 1),
            intArrayOf(0, 1, 0, 1),
            intArrayOf(0, 0, 0, 1)
        ),
        // ID 35
        arrayOf(
            intArrayOf(1, 1, 1, 1),
            intArrayOf(0, 1, 1, 0),
            intArrayOf(1, 1, 0, 1),
            intArrayOf(0, 1, 1, 0)
        ),
        // ID 36
        arrayOf(
            intArrayOf(1, 1, 1, 0),
            intArrayOf(0, 1, 1, 1),
            intArrayOf(1, 0, 0, 0),
            intArrayOf(1, 0, 1, 0)
        ),
        // ID 37
        arrayOf(
            intArrayOf(1, 1, 1, 1),
            intArrayOf(1, 0, 1, 1),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0)
        ),
        // ID 38
        arrayOf(
            intArrayOf(1, 1, 1, 1),
            intArrayOf(0, 0, 1, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(1, 0, 0, 1)
        ),
        // ID 39
        arrayOf(
            intArrayOf(1, 1, 1, 0),
            intArrayOf(0, 0, 1, 1),
            intArrayOf(1, 0, 1, 0),
            intArrayOf(0, 1, 0, 1)
        ),
        // ID 40
        arrayOf(
            intArrayOf(1, 1, 1, 0),
            intArrayOf(1, 0, 0, 0),
            intArrayOf(1, 1, 1, 0),
            intArrayOf(0, 1, 1, 1)
        ),
        // ID 41
        arrayOf(
            intArrayOf(1, 1, 0, 1),
            intArrayOf(0, 1, 0, 1),
            intArrayOf(1, 1, 0, 1),
            intArrayOf(0, 1, 1, 1)
        ),
        // ID 42
        arrayOf(
            intArrayOf(1, 1, 0, 0),
            intArrayOf(1, 1, 0, 1),
            intArrayOf(0, 1, 1, 1),
            intArrayOf(0, 0, 1, 1)
        ),
        // ID 43
        arrayOf(
            intArrayOf(1, 1, 0, 0),
            intArrayOf(0, 1, 1, 1),
            intArrayOf(0, 1, 0, 0),
            intArrayOf(1, 1, 0, 1)
        ),
        // ID 44
        arrayOf(
            intArrayOf(1, 1, 0, 1),
            intArrayOf(1, 0, 1, 1),
            intArrayOf(0, 0, 0, 1),
            intArrayOf(0, 1, 1, 1)
        ),
        // ID 45
        arrayOf(
            intArrayOf(1, 1, 0, 1),
            intArrayOf(0, 0, 0, 1),
            intArrayOf(0, 0, 0, 1),
            intArrayOf(0, 1, 0, 0)
        ),
        // ID 46
        arrayOf(
            intArrayOf(1, 1, 0, 1),
            intArrayOf(0, 0, 1, 0),
            intArrayOf(1, 1, 0, 0),
            intArrayOf(0, 0, 0, 0)
        ),
        // ID 47
        arrayOf(
            intArrayOf(1, 0, 1, 1),
            intArrayOf(0, 1, 0, 0),
            intArrayOf(1, 0, 0, 1),
            intArrayOf(1, 0, 1, 1)
        ),
        // ID 48
        arrayOf(
            intArrayOf(1, 0, 1, 0),
            intArrayOf(1, 1, 1, 1),
            intArrayOf(1, 1, 0, 1),
            intArrayOf(0, 0, 0, 1)
        ),
        // ID 49
        arrayOf(
            intArrayOf(1, 0, 1, 0),
            intArrayOf(1, 1, 1, 1),
            intArrayOf(1, 1, 1, 0),
            intArrayOf(1, 1, 0, 0)
        )
    )
}
