package eu.vaadinonkotlin.security

import com.vaadin.flow.server.VaadinRequest
import com.vaadin.flow.server.auth.ViewAccessChecker
import eu.vaadinonkotlin.VaadinOnKotlin
import java.security.Principal
import java.util.function.Function
import javax.servlet.http.HttpServletRequest

/**
 * Checks that a user is logged in. Uses standard Vaadin [ViewAccessChecker] but
 * obtains the user from [VaadinOnKotlin.loggedInUserResolver] rather than from
 * [HttpServletRequest.getUserPrincipal] and [HttpServletRequest.isUserInRole].
 *
 * * Don't forget to install a proper [VaadinOnKotlin.loggedInUserResolver] for your project.
 * * Install this as a [com.vaadin.flow.router.BeforeEnterListener] into your UI,
 *   usually via the [com.vaadin.flow.server.VaadinServiceInitListener].
 *
 * See [vok-security README](https://github.com/mvysny/vaadin-on-kotlin/blob/master/vok-security/README.md)
 * on how to use this class properly.
 */
public class VokViewAccessChecker : ViewAccessChecker() {
    override fun getPrincipal(request: VaadinRequest?): Principal? = VaadinOnKotlin.loggedInUserResolver.getCurrentUser()
    override fun getRolesChecker(request: VaadinRequest?): Function<String, Boolean> = Function { role ->
        VaadinOnKotlin.loggedInUserResolver.getCurrentUserRoles().contains(role)
    }
}
