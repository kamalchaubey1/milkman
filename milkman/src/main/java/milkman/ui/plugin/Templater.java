package milkman.ui.plugin;

/**
* a templater that modifies input-strings in an
* implementation-specific way, e.g. replaces environment-varables.
*/
public interface Templater {

    /**
    * returns a string with all replaced tags
    */
	public String replaceTags(String input);
}
