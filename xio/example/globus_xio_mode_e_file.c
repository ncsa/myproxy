#include "globus_xio.h"
#include "globus_xio_util.h"
#include "globus_xio_mode_e_driver.h"

#define CHUNK_SIZE 5000
#define FILE_NAME_LEN 25

void
test_res(
    globus_result_t                         res)
{
    if(res == GLOBUS_SUCCESS)
    {
        return;
    }

    fprintf(stderr, "ERROR: %s\n", globus_error_print_chain(
        globus_error_peek(res)));

    globus_assert(0);
}

void
help()
{
    fprintf(stdout, 
        "globus-xio-mode-e-file [options]\n"
        "-----------------\n"
        "using the -s switch sets up a server."  
        "\n"
        "specify -c <contact string> to communicate with the server\n"
        "\n"
        "options:\n"
        "-c <contact_string> : use this contact string (required for client)\n"
        "-s : be a server\n"
        "-p : num streams (optional)\n"
	"-f : file name\n");
}

int
main(
    int                                     argc,
    char **                                 argv)
{
    globus_xio_driver_t                     mode_e_driver;
    globus_xio_stack_t                      stack;
    globus_xio_handle_t                     xio_handle;
    globus_xio_server_t			    server;	
    globus_xio_attr_t                       attr = NULL;
    char *                                  cs = NULL;
    globus_result_t                         res;
    int                                     ctr;
    int					    num_streams = 1;
    globus_bool_t                           be_server = GLOBUS_FALSE;
    int                                     rc;
    char				    filename[FILE_NAME_LEN];
    FILE *				    fp;

    rc = globus_module_activate(GLOBUS_XIO_MODULE);
    globus_assert(rc == GLOBUS_SUCCESS);

    res = globus_xio_driver_load("mode_e", &mode_e_driver);
    test_res(res);
    res = globus_xio_stack_init(&stack, NULL);
    test_res(res);
    res = globus_xio_stack_push_driver(stack, mode_e_driver);
    test_res(res);

    if (argc < 4)
    {
        help();
        exit(1);
    }
    for(ctr = 1; ctr < argc; ctr++)
    {
        if(strcmp(argv[ctr], "-h") == 0)
        {
            help();
            return 0;
        }
        else if(strcmp(argv[ctr], "-c") == 0)
        {
	    if (argc < 5)
	    {
		help();
		exit(1);
	    }
            cs = argv[ctr + 1];
            ctr++;
        }
        else if(strcmp(argv[ctr], "-s") == 0)
        {
            be_server = GLOBUS_TRUE;
        }
        else if(strcmp(argv[ctr], "-p") == 0)
        {
	    if (argc < 6)
	    {
		help();
		exit(1);
	    }
            num_streams = atoi(argv[ctr+1]);
            test_res(globus_xio_attr_init(&attr));
            test_res(globus_xio_attr_cntl(
                attr,
                mode_e_driver,
                GLOBUS_XIO_MODE_E_SET_NUM_STREAMS,
                num_streams));
        } 
	else if(strcmp(argv[ctr], "-f") == 0)
	{
	    if (ctr + 1 < argc)
	    {
	        strcpy(filename, argv[ctr + 1]);
	    }
	    else	
	    {
		help();
		exit(1);
	    }
	}
    }
    
    if (!be_server && (!cs || !*cs))
    {
        help();
        exit(1);
    }
    

  
    if(be_server)
    {
        char                            buffer[CHUNK_SIZE + 1];
        int                             nbytes;
	int i;
	res = globus_xio_server_create(&server, attr, stack);
    	test_res(res);
        globus_xio_server_get_contact_string(server, &cs);
        fprintf(stdout, "Contact: %s\n", cs);   
	res = globus_xio_server_accept(&xio_handle, server);
    	test_res(res);
	res = globus_xio_open(xio_handle, NULL, attr);
	test_res(res);
 	fp = fopen(filename, "w");
        while(1)
        {
            for (i=0; i<CHUNK_SIZE + 1; i++)
		buffer[i] = '\0';
            res = globus_xio_read(
                xio_handle,
                buffer,
                sizeof(buffer) - 1,
		1,
                &nbytes,
                NULL);
            fputs(buffer, fp);
	    if (res != GLOBUS_SUCCESS)
		break;
	}
        fclose(fp);  
    }
    else
    {
        char                            buffer[CHUNK_SIZE + 1];
        int	                        nbytes;
        int				i,x;
        globus_xio_data_descriptor_t    dd;
        res = globus_xio_handle_create(&xio_handle, stack);
        test_res(res);
        res = globus_xio_stack_destroy(stack);
        test_res(res);
   	res = globus_xio_open(xio_handle, cs, attr);
   	test_res(res);
        fp = fopen(filename, "r");
        while(!feof(fp))
	{
 	    for (i = 0; i< CHUNK_SIZE + 1; i++)
                buffer[i] = '\0';
	    x = fread(buffer, CHUNK_SIZE, 1, fp);
            nbytes = strlen(buffer);
            res = globus_xio_write(
                xio_handle,
                buffer,
                nbytes,
                nbytes,
                &nbytes,
                NULL);
            test_res(res); 
        }

/*        test_res(globus_xio_data_descriptor_init(&dd, xio_handle));
	test_res(globus_xio_data_descriptor_cntl(
	    dd,
	    mode_e_driver,
	    GLOBUS_XIO_MODE_E_SEND_EOD,
	    GLOBUS_TRUE));
        res = globus_xio_write(
                xio_handle,
                buffer,
                nbytes,
                nbytes,
                &nbytes,
                NULL);
        test_res(res); */
        fclose(fp);
    }
    res = globus_xio_close(xio_handle, attr);
    test_res(res);

    res = globus_xio_driver_unload(mode_e_driver);
    test_res(res);
 
    rc = globus_module_deactivate(GLOBUS_XIO_MODULE);
    globus_assert(rc == GLOBUS_SUCCESS);

    return 0;
}
