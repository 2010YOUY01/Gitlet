package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.List;

public class Blob implements Serializable {
    public static String getFileContent(String filename) {
        File f = new File("./" + filename);
        return Utils.readContentsAsString(f);
    }

    public static String getFileHash(String filename) {
        return Utils.sha1(getFileContent(filename));
    }

    /**
     * Write the file in working-directory to .gitlet directory
     * @param filename File name in working directory, must exist.
     * @return SHA1 for the String of given filename's content
     */
    public static String writeFile(String filename) {
        String fileHash = getFileHash(filename);
        String fileContent = getFileContent(filename);
        File f = new File(Gitlet.OBJECT_PATH + fileHash);
        if (!f.exists()) Utils.writeContents(f, fileContent);
        return fileHash;
    }

    /**
     * Read file from .gitlet directory
     * @param sha1 the SHA1 hash of target file
     * @return content string inside the target file.
     */
    public static String readFile(String sha1) {
        File targetFile = new File(Gitlet.OBJECT_PATH + sha1);
        return Utils.readContentsAsString(targetFile);
    }

    /**
     * Write the Blob from .gitlet back to working directory.
     * @param sha1 the sha1 hash of the required object, can be shortened to <40
     *             file of that sha1 must exist.
     * @param fileName filename in the working directory to be overwritten.
     */
    public static void writeBackFromGit(String sha1, String fileName) {
        if (sha1.length() != Gitlet.SHA1_LENGTH) {
            List<String> objNameList = Utils.plainFilenamesIn(Gitlet.OBJECT_PATH);
            for (String objName : objNameList) {
                if (objName.contains(sha1)) {
                    sha1 = objName;
                    break;
                }
            }
        }
        File blobFile = new File(Gitlet.OBJECT_PATH + sha1);
        if (!blobFile.exists()) { Utils.error("writeBackFromGit Failed."); }
        String contents = Utils.readContentsAsString(blobFile);
        File actualFile = new File("./" + fileName);
        Utils.writeContents(actualFile, contents);
    }
}
