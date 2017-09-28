package com.example.unno.mywebrtc;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by Administrator on 2016-12-14.
 */

public class RecordFile {

    private String TAG = RecordFile.class.getSimpleName();
    private ArrayList<String> mRecFiles;
    private ArrayList<Long> mRecFilesNum;
    private HashMap<Long, String> fileMap;
    private String filePath;
    private String delFileName;

    public boolean deleteBeforeFile() {
        Log.d(TAG, "deleteBeforeFile");
        String delFname = getRecordFileList();
        Log.i(TAG, "delFname = " + delFname);

        File delfile = new File(filePath + delFname);
        if (delfile.delete()) {
            Log.i(TAG, "File deleted successfully");
            return true;
        } else {
            Log.i(TAG, "File deleted fail.");
            return false;
        }

    }

    public String getRecordFileList() {
        Log.d(TAG, "getRecordFileList");

        filePath = Environment.getExternalStorageDirectory() + File.separator
                + Environment.DIRECTORY_MOVIES + File.separator;

        Log.i(TAG, "filePath = " + filePath);

        mRecFiles = new ArrayList();
        mRecFilesNum = new ArrayList();

        fileMap = new HashMap();

        File file = new File(filePath);
        File[] list = file.listFiles();

        for (int i = 0; i < list.length; i++) {
            String fname = list[i].getName();
            Log.i(TAG, "fname = " + fname);

            int idx = fname.lastIndexOf(".");
            String fname2 = fname.substring(0, idx);
            Log.i(TAG, "fname2 = " + fname2);

            long tmp = Long.parseLong(fname2.replaceAll("[^0-9]", ""));
            Log.i(TAG, "tmp = " + Long.toString(tmp) + ", filename = " + list[i].getName());

            fileMap.put(tmp, fname);
            mRecFilesNum.add(tmp);
        }

        Log.i(TAG, "------------------------------------------------------");
        long max = Collections.max(mRecFilesNum);
        long min = Collections.min(mRecFilesNum);
        Log.i(TAG, "Max = " + max + ", iMin = " + min);
        Log.i(TAG, "------------------------------------------------------");
        Iterator<Long> iterator = fileMap.keySet().iterator();
        while (iterator.hasNext()) {
            Long key = iterator.next();
            Log.i(TAG, "key = " + Long.toString(key) + ", val = " + fileMap.get(key));

            if (key == min) {
                delFileName = fileMap.get(key);
                Log.i(TAG, "delete file name = " + delFileName);
                break;
            }
        }
        Log.i(TAG, "------------------------------------------------------");

        return delFileName;
    }


}
