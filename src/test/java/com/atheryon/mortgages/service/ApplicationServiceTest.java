package com.atheryon.mortgages.service;

import com.atheryon.mortgages.domain.entity.ApplicationParty;
import com.atheryon.mortgages.domain.entity.LoanApplication;
import com.atheryon.mortgages.domain.entity.Product;
import com.atheryon.mortgages.domain.entity.PropertySecurity;
import com.atheryon.mortgages.domain.enums.ApplicationStatus;
import com.atheryon.mortgages.domain.enums.Channel;
import com.atheryon.mortgages.domain.enums.LoanPurpose;
import com.atheryon.mortgages.exception.BusinessRuleException;
import com.atheryon.mortgages.repository.LoanApplicationRepository;
import com.atheryon.mortgages.repository.ProductRepository;
import com.atheryon.mortgages.repository.WorkflowEventRepository;
import com.atheryon.mortgages.statemachine.ApplicationStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock
    private LoanApplicationRepository applicationRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ApplicationStateMachine stateMachine;

    @Mock
    private WorkflowEventRepository workflowEventRepository;

    private ApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationService(applicationRepository, productRepository,
                stateMachine, workflowEventRepository);
    }

    @Test
    void create_savesApplicationWithDraftStatusAndGeneratedNumber() {
        LoanApplication request = new LoanApplication();
        request.setChannel(Channel.DIRECT_ONLINE);

        when(applicationRepository.save(any(LoanApplication.class))).thenAnswer(invocation -> {
            LoanApplication saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(workflowEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LoanApplication result = service.create(request);

        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.DRAFT);
        assertThat(result.getApplicationNumber()).isNotNull();
        assertThat(result.getApplicationNumber()).startsWith("ATH-");
        assertThat(result.getCreatedAt()).isNotNull();

        ArgumentCaptor<LoanApplication> captor = ArgumentCaptor.forClass(LoanApplication.class);
        verify(applicationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ApplicationStatus.DRAFT);
    }

    @Test
    void submit_validatesAndTransitionsToSubmitted() {
        UUID appId = UUID.randomUUID();
        Product product = new Product();
        product.setId(UUID.randomUUID());

        ApplicationParty party = new ApplicationParty();
        PropertySecurity security = new PropertySecurity();

        LoanApplication app = new LoanApplication();
        app.setId(appId);
        app.setStatus(ApplicationStatus.DRAFT);
        app.setRequestedAmount(new BigDecimal("500000"));
        app.setTermMonths(360);
        app.setPurpose(LoanPurpose.PURCHASE);
        app.setProduct(product);
        app.setApplicationParties(List.of(party));
        app.setSecurities(List.of(security));

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(stateMachine.transition(any(), eq(ApplicationStatus.SUBMITTED), any(), any()))
                .thenReturn(ApplicationStatus.SUBMITTED);
        when(applicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(workflowEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LoanApplication result = service.submit(appId);

        assertThat(result.getSubmittedAt()).isNotNull();
        verify(stateMachine).transition(app, ApplicationStatus.SUBMITTED, "SYSTEM", "SYSTEM");
    }

    @Test
    void submit_throwsBusinessRuleException_whenRequestedAmountIsNull() {
        UUID appId = UUID.randomUUID();
        LoanApplication app = new LoanApplication();
        app.setId(appId);
        app.setStatus(ApplicationStatus.DRAFT);
        app.setRequestedAmount(null);
        app.setTermMonths(360);
        app.setPurpose(LoanPurpose.PURCHASE);
        app.setProduct(new Product());
        app.setApplicationParties(List.of(new ApplicationParty()));
        app.setSecurities(List.of(new PropertySecurity()));

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> service.submit(appId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not ready for submission");
    }

    @Test
    void withdraw_transitionsToWithdrawnFromSubmitted() {
        UUID appId = UUID.randomUUID();
        LoanApplication app = new LoanApplication();
        app.setId(appId);
        app.setStatus(ApplicationStatus.SUBMITTED);

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(stateMachine.transition(any(), eq(ApplicationStatus.WITHDRAWN), any(), any()))
                .thenReturn(ApplicationStatus.WITHDRAWN);
        when(applicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(workflowEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LoanApplication result = service.withdraw(appId, "Changed mind", "user1");

        verify(stateMachine).transition(app, ApplicationStatus.WITHDRAWN, "user1", "USER");
    }

    @Test
    void update_throwsBusinessRuleException_whenStatusIsNotDraftOrInProgress() {
        UUID appId = UUID.randomUUID();
        LoanApplication app = new LoanApplication();
        app.setId(appId);
        app.setStatus(ApplicationStatus.SUBMITTED);

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));

        LoanApplication updates = new LoanApplication();
        updates.setRequestedAmount(new BigDecimal("600000"));

        assertThatThrownBy(() -> service.update(appId, updates))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("DRAFT or IN_PROGRESS");
    }

    @Test
    void update_appliesChangesInDraftStatus() {
        UUID appId = UUID.randomUUID();
        LoanApplication app = new LoanApplication();
        app.setId(appId);
        app.setStatus(ApplicationStatus.DRAFT);
        app.setRequestedAmount(new BigDecimal("500000"));

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(applicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LoanApplication updates = new LoanApplication();
        updates.setRequestedAmount(new BigDecimal("600000"));

        LoanApplication result = service.update(appId, updates);

        assertThat(result.getRequestedAmount()).isEqualByComparingTo(new BigDecimal("600000"));
    }

    @Test
    void submit_throwsBusinessRuleException_whenNoParties() {
        UUID appId = UUID.randomUUID();
        LoanApplication app = new LoanApplication();
        app.setId(appId);
        app.setStatus(ApplicationStatus.DRAFT);
        app.setRequestedAmount(new BigDecimal("500000"));
        app.setTermMonths(360);
        app.setPurpose(LoanPurpose.PURCHASE);
        app.setProduct(new Product());
        app.setApplicationParties(List.of());
        app.setSecurities(List.of(new PropertySecurity()));

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> service.submit(appId))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void submit_throwsBusinessRuleException_whenNoSecurities() {
        UUID appId = UUID.randomUUID();
        LoanApplication app = new LoanApplication();
        app.setId(appId);
        app.setStatus(ApplicationStatus.DRAFT);
        app.setRequestedAmount(new BigDecimal("500000"));
        app.setTermMonths(360);
        app.setPurpose(LoanPurpose.PURCHASE);
        app.setProduct(new Product());
        app.setApplicationParties(List.of(new ApplicationParty()));
        app.setSecurities(List.of());

        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> service.submit(appId))
                .isInstanceOf(BusinessRuleException.class);
    }
}
