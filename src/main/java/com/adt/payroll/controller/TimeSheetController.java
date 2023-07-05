package com.adt.payroll.controller;

import java.text.ParseException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import com.adt.payroll.dto.CheckStatusDTO;
import com.adt.payroll.dto.TimesheetDTO;
import com.adt.payroll.event.OnPriorTimeAcceptOrRejectEvent;
import com.adt.payroll.event.OnPriorTimeDetailsSavedEvent;
import com.adt.payroll.exception.PriorTimeAdjustmentException;
import com.adt.payroll.model.Priortime;
import com.adt.payroll.model.TimeSheetModel;
import com.adt.payroll.model.payload.ApiResponse;
import com.adt.payroll.model.payload.PriorTimeManagementRequest;
import com.adt.payroll.msg.ResponseModel;
import com.adt.payroll.repository.PriorTimeRepository;
import com.adt.payroll.repository.TimeSheetRepo;
import com.adt.payroll.service.TimeSheetService;

@RestController
@RequestMapping("/timeSheet")
public class TimeSheetController {

	private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

	@Autowired
	private TimeSheetService timeSheetService;

	@Autowired
	ApplicationEventPublisher applicationEventPublisher;

	@Autowired
	TimeSheetRepo timeSheetRepo;

	@Autowired
	PriorTimeRepository priorTimeRepository;

	@PreAuthorize("@auth.allow('ROLE_USER',T(java.util.Map).of('currentUser', #empId))")
	@PostMapping("/checkIn/{empId}")
	public ResponseEntity<String> saveCheckIn(@PathVariable int empId, HttpServletRequest request)
			throws ParseException {
		LOGGER.info("API Call From IP: " + request.getRemoteHost());
		return ResponseEntity.ok(timeSheetService.updateCheckIn(empId));
	}

	@PreAuthorize("@auth.allow('ROLE_USER',T(java.util.Map).of('currentUser', #empId))")
	@PutMapping("/checkOut/{empId}")
	public ResponseEntity<String> saveCheckOut(@PathVariable int empId, HttpServletRequest request)
			throws ParseException {
		LOGGER.info("API Call From IP: " + request.getRemoteHost());
		return new ResponseEntity<>(timeSheetService.updateCheckOut(empId), HttpStatus.OK);
	}

	@PreAuthorize("@auth.allow('ROLE_USER',T(java.util.Map).of('currentUser', #empId))")
	@PostMapping("/checkStatus/{empId}")
	public ResponseEntity<CheckStatusDTO> checkStatus(@PathVariable int empId, HttpServletRequest request) {
		LOGGER.info("API Call From IP: " + request.getRemoteHost());
		return new ResponseEntity<>(timeSheetService.checkStatus(empId), HttpStatus.OK);
	}

	@PreAuthorize("@auth.allow('ROLE_USER',T(java.util.Map).of('currentUser', #empId))")
	@GetMapping("/priorTimeAdjustment/{empId}")
	public ResponseEntity<ResponseModel> priorTimeAdjustment(@PathVariable int empId, HttpServletRequest request) {
		LOGGER.info("API Call From IP: " + request.getRemoteHost());
		return new ResponseEntity<>(timeSheetService.checkPriorStatus(empId), HttpStatus.OK);
	}
//--------------------------------------------------------------------------------------------------------------------------------
	
	@PreAuthorize("@auth.allow('ROLE_USER',T(java.util.Map).of('currentUser', #empId))")
	@GetMapping("/empAttendence")
	public ResponseEntity<List<TimesheetDTO>> empAttendence(@RequestParam("empId") int empId,
			@RequestParam("fromDate") @DateTimeFormat(pattern = "dd-MM-yyyy") String fromDate,
			@RequestParam("toDate") @DateTimeFormat(pattern = "dd-MM-yyyy") String toDate,
			HttpServletRequest request) {
		LOGGER.info("API Call From IP: " + request.getRemoteHost());
		
		return new ResponseEntity<>(timeSheetService.empAttendence(empId, fromDate, toDate), HttpStatus.OK);
	}

	@PreAuthorize("@auth.allow('ROLE_ADMIN')")
	@GetMapping("/allEmpAttendence")
	public ResponseEntity<List<TimeSheetModel>> allEmpAttendence(
			@RequestParam("fromDate") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate fromDate,
			@RequestParam("toDate") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate toDate,
			HttpServletRequest request) {
		LOGGER.info("API Call From IP: " + request.getRemoteHost());
		return new ResponseEntity<>(timeSheetService.allEmpAttendence(fromDate, toDate), HttpStatus.OK);
	}

	@PreAuthorize("@auth.allow('ROLE_USER')")
	@PostMapping("/updatePriorTime")
	public ResponseEntity<ApiResponse> updatePriorTimeByDate(@RequestBody PriorTimeManagementRequest priorTimeManagementRequest,
			HttpServletRequest request) throws ParseException {
		
		LOGGER.info("API Call From IP: " + request.getRemoteHost());
		
		return ((Optional<Priortime>) timeSheetService.savePriorTime(priorTimeManagementRequest)).map(priorTimeuser -> {
			int priortimeId = priorTimeuser.getPriortimeId();
			
			UriComponentsBuilder urlBuilder1 = ServletUriComponentsBuilder.fromCurrentContextPath()
					.path("/timeSheet/updatePriorTime/Accepted/" + priortimeId);
			UriComponentsBuilder urlBuilder2 = ServletUriComponentsBuilder.fromCurrentContextPath()
					.path("/timeSheet/updatePriorTime/Rejected/" + priortimeId);
			OnPriorTimeDetailsSavedEvent onPriorTimeDetailsSavedEvent = new OnPriorTimeDetailsSavedEvent(priorTimeuser,
					urlBuilder1, urlBuilder2);
			applicationEventPublisher.publishEvent(onPriorTimeDetailsSavedEvent);
			
			return ResponseEntity.ok(new ApiResponse(true, "Mail sent successfully."));
		}).orElseThrow(() -> new PriorTimeAdjustmentException(priorTimeManagementRequest.getEmail(),
				"Missing user details in database"));
	}

	@PreAuthorize("@auth.allow('ROLE_ADMIN')")
	@GetMapping("/updatePriorTime/Accepted/{priortimeId}")
	public ResponseEntity<ApiResponse> updatePriorTimeAccepted(@PathVariable(name = "priortimeId") int priortimeId,
			HttpServletRequest request) throws ParseException {
		LOGGER.info("API Call From IP: " + request.getRemoteHost());
		Optional<Priortime> priortime = priorTimeRepository.findById(priortimeId);
		timeSheetService.saveConfirmedDetails(priortime);
		priortime.get().setStatus("Accepted");
		priorTimeRepository.save(priortime.get());
		String email = priortime.get().getEmail();
		OnPriorTimeAcceptOrRejectEvent onPriortimeApprovalEvent = new OnPriorTimeAcceptOrRejectEvent(priortime,
				"PriorTimesheet Entry", "Approved");

		applicationEventPublisher.publishEvent(onPriortimeApprovalEvent);
		return ResponseEntity.ok(new ApiResponse(true, "Details for PriorTime Timesheet entry updated successfully"));

	}

	@PreAuthorize("@auth.allow('ROLE_ADMIN')")
	@GetMapping("/updatePriorTime/Rejected/{priortimeId}")
	public ResponseEntity<ApiResponse> updatePriorTimeRejected(@PathVariable(name = "priortimeId") int priortimeId,
			HttpServletRequest request) {
		LOGGER.info("API Call From IP: " + request.getRemoteHost());
		Optional<Priortime> priortime = priorTimeRepository.findById(priortimeId);
		priortime.get().setStatus("Rejected");
		priorTimeRepository.save(priortime.get());
		OnPriorTimeAcceptOrRejectEvent onPriortimeApprovalEvent = new OnPriorTimeAcceptOrRejectEvent(priortime,
				"PriorTimesheet Entry", "Rejected");

		applicationEventPublisher.publishEvent(onPriortimeApprovalEvent);
		return ResponseEntity.ok(new ApiResponse(true, "Details for PriorTime Timesheet entry updated as Rejected"));

	}

	@PreAuthorize("@auth.allow('ROLE_USER',T(java.util.Map).of('currentUser', #empId))")
	@PutMapping("/pause/{empId}")
	public ResponseEntity<String> pauseWorkingTime(@PathVariable int empId, HttpServletRequest request) {
		LOGGER.info("API Call From IP: " + request.getRemoteHost());
		return new ResponseEntity<>(timeSheetService.pauseWorkingTime(empId), HttpStatus.OK);
	}

	@PreAuthorize("@auth.allow('ROLE_USER',T(java.util.Map).of('currentUser', #empId))")
	@PatchMapping("/resume/{empId}")
	public ResponseEntity<String> resumeWorkingTime(@PathVariable int empId, HttpServletRequest request)
			throws ParseException {
		LOGGER.info("API Call From IP: " + request.getRemoteHost());
		return new ResponseEntity<>(timeSheetService.resumeWorkingTime(empId), HttpStatus.OK);
	}
}
