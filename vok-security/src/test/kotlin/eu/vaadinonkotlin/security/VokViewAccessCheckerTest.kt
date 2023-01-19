package eu.vaadinonkotlin.security

import com.github.mvysny.dynatest.DynaTest
import com.github.mvysny.dynatest.expectThrows
import com.github.mvysny.kaributesting.v10.MockVaadin
import com.github.mvysny.kaributesting.v10.Routes
import com.github.mvysny.kaributesting.v10._expectInternalServerError
import com.github.mvysny.kaributesting.v10.expectView
import com.github.mvysny.kaributesting.v10.mock.MockedUI
import com.github.mvysny.kaributools.navigateTo
import com.vaadin.flow.component.UI
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.NotFoundException
import com.vaadin.flow.router.Route
import com.vaadin.flow.router.RouterLayout
import com.vaadin.flow.server.VaadinRequest
import eu.vaadinonkotlin.VaadinOnKotlin
import java.security.Principal
import javax.annotation.security.PermitAll
import javax.annotation.security.RolesAllowed

private fun checkUIThread(): UI = UI.getCurrent() ?: throw IllegalStateException("Not in UI thread, or UI.init() is currently ongoing")

object DummyUserResolver : LoggedInUserResolver {
    var userWithRoles: Set<String>? = null
    override fun getCurrentUser(): Principal? {
        checkUIThread()
        return if (userWithRoles == null) null else BasicUserPrincipal("dummy")
    }
    override fun getCurrentUserRoles(): Set<String> {
        checkUIThread()
        return userWithRoles ?: setOf()
    }
}

/**
 * A view with no parent layout.
 */
@RolesAllowed("admin")
@Route("admin")
class AdminView : VerticalLayout()

@Route("login")
class LoginView : VerticalLayout()

class MyLayout : VerticalLayout(), RouterLayout

@Route("", layout = MyLayout::class)
@PermitAll
class WelcomeView : VerticalLayout()

/**
 * A view with parent layout.
 */
@Route("user", layout = MyLayout::class)
@RolesAllowed("user")
class UserView : VerticalLayout()

@RolesAllowed("sales")
class SalesLayout : VerticalLayout(), RouterLayout

/**
 * This view can not be effectively viewed with 'user' since its parent layout lacks the 'user' role.
 */
@RolesAllowed("sales", "user")
@Route("sales/sale", layout = SalesLayout::class)
class SalesView : VerticalLayout()

/**
 * This view can not be effectively viewed with anybody.
 */
@RolesAllowed()
@Route("rejectall")
class RejectAllView : VerticalLayout()

class VokViewAccessCheckerTest : DynaTest({
    group("ViewAccessChecker") {
        lateinit var routes: Routes
        beforeGroup {
            VaadinOnKotlin.loggedInUserResolver = DummyUserResolver
            routes = Routes().autoDiscoverViews("eu.vaadinonkotlin.security")
        }
        beforeEach {
            DummyUserResolver.userWithRoles = null
            MockVaadin.setup(routes, uiFactory = { MockedUIWithViewAccessChecker() })
        }
        afterEach {
            MockVaadin.tearDown()
            DummyUserResolver.userWithRoles = null
        }

        test("no user logged in") {
            DummyUserResolver.userWithRoles = null
            navigateTo<AdminView>()
            expectView<LoginView>()

            navigateTo<UserView>()
            expectView<LoginView>()

            navigateTo<SalesView>()
            expectView<LoginView>()

            navigateTo<RejectAllView>()
            expectView<LoginView>()

            navigateTo<LoginView>()
            expectView<LoginView>()
        }
        test("admin logged in") {
            DummyUserResolver.userWithRoles = setOf("admin")
            navigateTo<AdminView>()
            expectView<AdminView>()

            // Vaadin 23.1.0 throws NotFoundException instead of redirecting to LoginView if user is logged in.
            expectThrows<NotFoundException>("No route found for 'user': Access denied") {
                navigateTo<UserView>()
            }

            expectThrows<NotFoundException>("No route found for 'sales/sale': Access denied") {
                navigateTo<SalesView>()
            }

            expectThrows<NotFoundException>("No route found for 'rejectall': Access denied") {
                navigateTo<RejectAllView>()
            }

            // VokViewAccessChecker won't navigate away from LoginView - it's the app's
            // responsibility to navigate to some welcome view after successful login.
            navigateTo<LoginView>()
            expectView<LoginView>()
        }
        test("user logged in") {
            DummyUserResolver.userWithRoles = setOf("user")

            // Vaadin 23.1.0 throws NotFoundException instead of redirecting to LoginView if user is logged in.
            expectThrows<NotFoundException>("No route found for 'admin': Access denied") {
                navigateTo<AdminView>()
            }

            navigateTo<UserView>()
            expectView<UserView>()

            navigateTo<SalesView>()
            expectView<SalesView>()

            // Vaadin 23.1.0 throws NotFoundException instead of redirecting to LoginView if user is logged in.
            expectThrows<NotFoundException>("No route found for 'rejectall': Access denied") {
                navigateTo<RejectAllView>()
            }

            // VokViewAccessChecker won't navigate away from LoginView - it's the app's
            // responsibility to navigate to some welcome view after successful login.
            navigateTo<LoginView>()
            expectView<LoginView>()
        }
        test("sales logged in") {
            DummyUserResolver.userWithRoles = setOf("sales")

            // Vaadin 23.1.0 throws NotFoundException instead of redirecting to LoginView if user is logged in.
            expectThrows<NotFoundException>("No route found for 'admin': Access denied") {
                navigateTo<AdminView>()
            }

            expectThrows<NotFoundException>("No route found for 'user': Access denied") {
                navigateTo<UserView>()
            }

            navigateTo<SalesView>()
            expectView<SalesView>()

            expectThrows<NotFoundException>("No route found for 'rejectall': Access denied") {
                navigateTo<RejectAllView>()
            }

            // VokViewAccessChecker won't navigate away from LoginView - it's the app's
            // responsibility to navigate to some welcome view after successful login.
            navigateTo<LoginView>()
            expectView<LoginView>()
        }
        test("error route not hijacked by the LoginView") {
            UI.getCurrent().addBeforeEnterListener { e ->
                e.rerouteToError(RuntimeException("Simulated"), "Simulated")
            }
            navigateTo(WelcomeView::class)
            _expectInternalServerError("Simulated")
        }
    }
})

class MockedUIWithViewAccessChecker : MockedUI() {
    override fun init(request: VaadinRequest) {
        super.init(request)
        val checker = VokViewAccessChecker()
        checker.setLoginView(LoginView::class.java)
        addBeforeEnterListener(checker)
    }
}
