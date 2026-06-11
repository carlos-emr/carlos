// ...

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.webserv.rest.RestResponse;

// ...

@Path("/demographic")
public class DemographicService {

    // ...

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public RestResponse addDemographic(DemographicTo1 demographicTo1) {
        if (!SecurityInfoManager.hasPrivilege("_demographic w")) {
            return RestResponse.errorResponse(403, "Forbidden");
        }
        // existing code...
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public RestResponse updateDemographic(DemographicTo1 demographicTo1) {
        if (!SecurityInfoManager.hasPrivilege("_demographic w")) {
            return RestResponse.errorResponse(403, "Forbidden");
        }
        // existing code...
    }

    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public RestResponse deleteDemographic(@PathParam("id") Integer id) {
        if (!SecurityInfoManager.hasPrivilege("_demographic d")) {
            return RestResponse.errorResponse(403, "Forbidden");
        }
        // existing code...
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public RestResponse getDemographic(@PathParam("id") Integer id) {
        if (!SecurityInfoManager.hasPrivilege("_demographic r")) {
            return RestResponse.errorResponse(403, "Forbidden");
        }
        // existing code...
    }

    @POST
    @Path("/search")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public RestResponse searchDemographic(DemographicSearchRequest demographicSearchRequest) {
        if (!SecurityInfoManager.hasPrivilege("_demographic r")) {
            return RestResponse.errorResponse(403, "Forbidden");
        }
        // existing code...
    }

    // ...
}