# V8 Java Adapter
This project is an add-on for the excellent [J2V8 Project](https://github.com/eclipsesource/J2V8), allowing users to easily share Java classes and objects with a V8 runtime.

## Getting The V8 Java Adapter
As this project is built on top of the J2V8 project, you will need to include two dependencies in your pom.xml:

1. A dependency for your preferred J2V8 artifact (e.g., `j2v8_macosx_x86_64` or `j2v8_win32_x86_64`).
2. This project.

An example `<dependencies>` section for a project that uses J2V8 on Linux would look like this:

    <dependencies>
        <!-- J2V8 Adapter -->
        <dependency>
            <groupId>io.alicorn.v8</groupId>
            <artifactId>v8-adapter</artifactId>
            <version>1.1</version>

        <!-- J2V8 Runtime -->
        <dependency>
            <groupId>com.eclipsesource.j2v8</groupId>
            <artifactId>j2v8_linux_x86_64</artifactId>
            <version>4.5.0</version>
        </dependency>
    </dependencies>

## Using The V8 Java Adapter
Once you have the adapter included, the only class you need to use is the `V8JavaAdapter` class. This class enables you to inject Java objects and classes into the V8 runtime.

Below is a brief example of injecting the Java [BitSet](https://docs.oracle.com/javase/7/docs/api/java/util/BitSet.html) class:

    public static void main(String[] args) {

        // Create the V8 runtime.
        v8 = V8.createV8Runtime();

        // Inject the Bit Set class into the V8 runtime.
        V8JavaAdapter.injectClass(BitSet.class, v8);

        // Create a Bit Set in JS and manipulate it.
        v8.executeVoidScript("var b = new BitSet(8);");
        v8.executeVoidScript("b.set(1);");
        v8.executeVoidScript("b.set(3);");
        v8.executeVoidScript("b.set(5);");

        // Prints out "{1, 3, 5}".
        System.out.println(v8.executeStringScript("b.toString();"));
    }

Below is an example of injecting an ***instance*** of the Java BitSet class into J2V8:

    public static void main(String[] args) {

        // Create the V8 runtime.
        v8 = V8.createV8Runtime();

        // Create a Bit Set in Java and manipulate it.
        BitSet javaBitSet = new BitSet(8);
        javaBitSet.set(0);
        javaBitSet.set(2);
        javaBitSet.set(4);

        // Inject the Bit Set instance into the V8 runtime.
        // The first parameter "b" is the name we want the
        // injected object to have in V8.
        V8JavaAdapter.injectObject("b", javaBitSet, v8);

        // Prints out "{0, 2, 4}".
        System.out.println(v8.executeStringScript("b.toString();"));
    }

## Roadmap
This project is production-ready and is already in use in the [Alicorn](http://alicorn.io) framework. However, there are still features to be added and optimizations to be made! Below is a list that contains some of the things we are either planning to add or in the process of adding (feel free to suggest additions to this list or make pull requests that address items on this list):

- JSR 223 (Scripting for the Java Platform) support

## Licensing and Contributing
This project is licensed under the BSD 3-Clause License, chosen for its inherent patent and trademark protection as well as its clear language. If enough/any issues are filed regarding this license choice (a departure from Apache V2 or the Eclipse Public License used by the J2V8 project), we will change the license to suit popular opinion.

Please feel free to contribute any pull requests, feature requests, or issues that you notice to this project; we want it to be the best it can be!
