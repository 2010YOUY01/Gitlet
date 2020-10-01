package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class Commit implements Serializable {
    private String message;
    private Date time;
    private String[] parent;
    private HashMap<String, String> blobs;


    /**
     * Commit object constructor
     * @param message commit message
     * @param time commit time;
     * @param parent SHA-1 of parent Commit object
     * @param blobs HashMap filename -> SHA-1 hash of blobs
     */
    public Commit(String message, Date time, String[] parent, HashMap<String, String> blobs) {
        this.message = message;
        this.time = time;
        this.parent = parent;
        this.blobs = blobs;
    }

    public HashMap<String, String> getBlobsMap() { return this.blobs; }
    public String getMessage() { return this.message; }
    public Date getTime() { return this.time; }

    /**
     * Get the commit SHA1 of parent.
     * @return return the 1st parent of a SHA1. For initial commit, return null.
     */
    public boolean hasTwoParents() {
        if (this.parent == null) return false;
        return (this.parent.length == 2);
    }

    public String getParentSHA1() {
        if (this.parent == null) return null;
        return this.parent[0];
    }

    public String getParent2SHA1() {
        if (this.parent == null || this.parent.length == 1) return null;
        return this.parent[1];
    }

    /**
     * Write this object to ./.gitlet/objects/sha1_of_this
     * @return SHA1 of this object
     */
    public String writeObject() {
        String commitSHA1 = Utils.sha1(Utils.serialize(this));
        File commitFile = new File( Gitlet.OBJECT_PATH + commitSHA1);
        Utils.writeObject(commitFile, this);
        return commitSHA1;
    }

    public static String abbrevSHA1toFull(String sha1) {
        String result = null;
        if (sha1.length() != Gitlet.SHA1_LENGTH) {
            List<String> objNameList = Utils.plainFilenamesIn(Gitlet.OBJECT_PATH);
            for (String objName : objNameList) {
                if (objName.contains(sha1)) {
                    result = objName;
                    break;
                }
            }
            return result;
        } else {
            return sha1;
        }
    }
    /**
     * Read commit object from .gitlet directory
     * @param sha1 sha1 hash of the target object, can be shortened to <40 length
     * @return Commit object with input sha1. If input is null or the object is not Commit, return null.
     */
    public static Commit readObject(String sha1) {
        if (sha1 == null) return null;
        sha1 = abbrevSHA1toFull(sha1);
        File commitFile = new File(Gitlet.OBJECT_PATH + sha1);
        Commit result = null;
        try {
            result = Utils.readObject(commitFile, Commit.class);
        } catch (IllegalArgumentException e) {
            result = null;
        }
        return result;
    }

    public void writeWorkingDir() {
        List<String> filesList = Utils.plainFilenamesIn("./");
        filesList.forEach((filename) -> {
            Utils.restrictedDelete("./" + filename);
        });

        blobs.forEach((fileName, fileHash) -> {
            File blobFile = new File(Gitlet.OBJECT_PATH + fileHash);
            String contents = Utils.readContentsAsString(blobFile);
            File actualFile = new File("./" + fileName);
            Utils.writeContents(actualFile, contents);
        });
    }
}