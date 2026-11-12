import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { IncidentSearchComponent } from './incident-search.component';

const SEARCH_URL = '/api/incidents/search';
const SMART_SEARCH_URL = '/api/incidents/smart-search';

const smartResponse = {
  message: 'Similar incidents found',
  results: matchingResults(),
  aiAnalysis: {
    possibleRootCauses: ['An unindexed database query'],
    explanation: 'The query may scan more rows than necessary.',
    troubleshootingSteps: ['Inspect slow query logs', 'Review the query execution plan'],
    preventiveMeasures: ['Monitor query latency'],
    additionalRecommendations: ['Load test the affected endpoint'],
  },
};

function matchingResults() {
  return [
    {
      incidentId: 1,
      title: 'Application API Response Slow',
      similarityPercentage: 91.9,
      resolution: 'Optimized frequently executed database queries.',
    },
    {
      incidentId: 2,
      title: 'Database Connection Pool Exhaustion',
      similarityPercentage: 28.5,
      resolution: 'Reviewed pool configuration and released leaked connections.',
    },
    {
      incidentId: 5,
      title: 'Application Login Failure',
      similarityPercentage: 19.8,
      resolution: 'Reviewed authentication logs and corrected security settings.',
    },
    {
      incidentId: 6,
      title: 'Extra incident',
      similarityPercentage: 12,
      resolution: 'This fourth incident should not be displayed.',
    },
  ];
}

const matchingResponse = {
  message: 'Similar incidents found',
  results: matchingResults(),
};

describe('IncidentSearchComponent', () => {
  let fixture: ComponentFixture<IncidentSearchComponent>;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [IncidentSearchComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    fixture = TestBed.createComponent(IncidentSearchComponent);
    httpTestingController = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('validates an empty description without making an API request', () => {
    clickSearch();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'Enter an incident description to search.',
    );
    httpTestingController.expectNone(SEARCH_URL);
  });

  it('sends a search request and displays up to three matching incidents', () => {
    setDescription('  API requests are slow because of database queries.  ');
    clickSearch();

    const request = httpTestingController.expectOne(SEARCH_URL);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      description: 'API requests are slow because of database queries.',
    });
    request.flush(matchingResponse);
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelectorAll('.result-card')).toHaveLength(3);
    expect(page.textContent).toContain('Application API Response Slow');
    expect(page.textContent).toContain('91.9%');
    expect(page.textContent).toContain('Optimized frequently executed database queries.');
    expect(page.textContent).not.toContain('Extra incident');
  });

  it('sends smart searches to the AI endpoint and separates AI suggestions from history', () => {
    setDescription('API requests are slow because of database queries.');
    clickSmartSearch();

    const request = httpTestingController.expectOne(SMART_SEARCH_URL);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      description: 'API requests are slow because of database queries.',
    });
    request.flush(smartResponse);
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelectorAll('.result-card')).toHaveLength(3);
    expect(page.textContent).toContain('VERIFIED HISTORICAL RESOLUTIONS');
    expect(page.textContent).toContain('VERIFIED RESOLUTION');
    expect(page.textContent).toContain('AI-GENERATED GUIDANCE');
    expect(page.textContent).toContain('An unindexed database query');
    expect(page.textContent).toContain('The query may scan more rows than necessary.');
    expect(page.textContent).toContain('Inspect slow query logs');
    expect(page.textContent).toContain('Monitor query latency');
    expect(page.textContent).toContain('Load test the affected endpoint');
  });

  it('shows the AI-unavailable response when Smart Search fails with HTTP 503', () => {
    setDescription('API requests are slow.');
    clickSmartSearch();
    httpTestingController.expectOne(SMART_SEARCH_URL).flush(
      { message: 'AI analysis is temporarily unavailable. Please try again later.' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'AI analysis is temporarily unavailable. Please try again later.',
    );
    expect(fixture.nativeElement.querySelectorAll('.result-card')).toHaveLength(0);
  });

  it('shows historical results and an AI-unavailable notice when smart search has no analysis', () => {
    setDescription('API requests are slow.');
    clickSmartSearch();
    httpTestingController.expectOne(SMART_SEARCH_URL).flush(matchingResponse);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.result-card')).toHaveLength(3);
    expect(fixture.nativeElement.textContent).toContain('AI analysis unavailable');
    expect(fixture.nativeElement.querySelector('.ai-analysis')).toBeNull();
  });

  it('shows a no-match state when the API returns no results', () => {
    setDescription('qzxwkjh vbnmty');
    clickSearch();
    httpTestingController.expectOne(SEARCH_URL).flush({
      message: 'No sufficiently similar resolved incident found.',
      results: [],
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No close matches found');
    expect(fixture.nativeElement.textContent).toContain(
      'No sufficiently similar resolved incident found.',
    );
    expect(fixture.nativeElement.querySelectorAll('.result-card')).toHaveLength(0);
  });

  it('shows the backend validation message for HTTP 400', () => {
    setDescription('invalid');
    clickSearch();
    httpTestingController.expectOne(SEARCH_URL).flush(
      { message: 'description must not be blank' },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('description must not be blank');
    expect(fixture.nativeElement.querySelectorAll('.result-card')).toHaveLength(0);
    expect(fixture.componentInstance.loading()).toBe(false);
  });

  it('shows a useful message for HTTP 500', () => {
    setDescription('service failure');
    clickSearch();
    httpTestingController.expectOne(SEARCH_URL).flush(
      { message: 'An unexpected server error occurred.' },
      { status: 500, statusText: 'Internal Server Error' },
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'The server encountered an error while searching.',
    );
    expect(fixture.nativeElement.querySelectorAll('.result-card')).toHaveLength(0);
  });

  it('identifies a development-proxy failure as an unavailable backend', () => {
    setDescription('backend is offline');
    clickSearch();
    httpTestingController.expectOne(SEARCH_URL).flush(
      'Error occurred while trying to proxy: ECONNREFUSED',
      { status: 500, statusText: 'Internal Server Error' },
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'Unable to reach the incident service.',
    );
  });

  it('reports when the backend cannot be reached', () => {
    setDescription('backend offline');
    clickSearch();
    httpTestingController
      .expectOne(SEARCH_URL)
      .error(new ProgressEvent('error'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'Unable to reach the incident service.',
    );
    expect(fixture.nativeElement.querySelectorAll('.result-card')).toHaveLength(0);
  });

  it('shows a loading state and prevents duplicate searches while a request is pending', () => {
    setDescription('database is slow');
    clickSearch();
    clickSearch();

    const request = httpTestingController.expectOne(SEARCH_URL);
    expect(fixture.componentInstance.loading()).toBe(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.loading-state')).not.toBeNull();
    expect(
      fixture.nativeElement.querySelector('.standard-search-button').disabled,
    ).toBe(true);
    expect(fixture.nativeElement.querySelector('.smart-search-button').disabled).toBe(true);
    httpTestingController.expectNone(SEARCH_URL);

    request.flush({ message: 'No matches', results: [] });
    fixture.detectChanges();
    expect(fixture.componentInstance.loading()).toBe(false);
    expect(fixture.nativeElement.querySelector('.loading-state')).toBeNull();
  });

  it('clears the description and all search results', () => {
    setDescription('database is slow');
    clickSearch();
    httpTestingController.expectOne(SEARCH_URL).flush(matchingResponse);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('.clear-button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.componentInstance.description()).toBe('');
    expect(fixture.componentInstance.results()).toEqual([]);
    expect(fixture.componentInstance.hasSearched()).toBe(false);
    expect(fixture.nativeElement.querySelectorAll('.result-card')).toHaveLength(0);
  });

  it('clears AI analysis when clearing a smart search', () => {
    setDescription('database is slow');
    clickSmartSearch();
    httpTestingController.expectOne(SMART_SEARCH_URL).flush(smartResponse);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('.clear-button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.componentInstance.aiAnalysis()).toBeUndefined();
    expect(fixture.nativeElement.querySelector('.ai-analysis')).toBeNull();
  });

  it('reports empty and malformed successful responses without showing recommendations', () => {
    setDescription('malformed response');
    clickSearch();
    httpTestingController.expectOne(SEARCH_URL).flush(null);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'The server returned an empty response.',
    );
    expect(fixture.nativeElement.querySelectorAll('.result-card')).toHaveLength(0);

    setDescription('wrong response shape');
    clickSearch();
    httpTestingController
      .expectOne(SEARCH_URL)
      .flush({ message: 'Unexpected', results: [{ title: 'Incomplete result' }] });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'The server returned an unexpected response.',
    );
    expect(fixture.nativeElement.querySelectorAll('.result-card')).toHaveLength(0);
  });

  function setDescription(value: string): void {
    const textarea = fixture.nativeElement.querySelector(
      '#incident-description',
    ) as HTMLTextAreaElement;
    textarea.value = value;
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function clickSearch(): void {
    (
      fixture.nativeElement.querySelector('.standard-search-button') as HTMLButtonElement
    ).click();
    fixture.detectChanges();
  }

  function clickSmartSearch(): void {
    (
      fixture.nativeElement.querySelector('.smart-search-button') as HTMLButtonElement
    ).click();
    fixture.detectChanges();
  }
});
