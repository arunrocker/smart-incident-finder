import { DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { IncidentSearchResponse, IncidentSearchResult } from '../../models/incident.model';
import { IncidentService } from '../../services/incident.service';

@Component({
  selector: 'app-incident-search',
  imports: [DecimalPipe],
  templateUrl: './incident-search.component.html',
  styleUrl: './incident-search.component.css',
})
export class IncidentSearchComponent {
  private readonly incidentService = inject(IncidentService);

  readonly description = signal('');
  readonly validationMessage = signal('');
  readonly errorMessage = signal('');
  readonly responseMessage = signal('');
  readonly results = signal<IncidentSearchResult[]>([]);
  readonly aiAnalysis = signal<IncidentSearchResponse['aiAnalysis']>(undefined);
  readonly searchMode = signal<'standard' | 'smart'>('standard');
  readonly loading = signal(false);
  readonly hasSearched = signal(false);

  updateDescription(event: Event): void {
    const target = event.target;
    if (!(target instanceof HTMLTextAreaElement)) {
      return;
    }

    this.description.set(target.value);
    this.validationMessage.set('');
  }

  search(mode: 'standard' | 'smart' = 'standard'): void {
    if (this.loading()) {
      return;
    }

    const description = this.description().trim();
    if (!description) {
      this.validationMessage.set('Enter an incident description to search.');
      return;
    }

    if (description.length > 5000) {
      this.validationMessage.set('Descriptions must be 5000 characters or fewer.');
      return;
    }

    this.validationMessage.set('');
    this.errorMessage.set('');
    this.responseMessage.set('');
    this.results.set([]);
    this.aiAnalysis.set(undefined);
    this.hasSearched.set(false);
    this.searchMode.set(mode);
    this.loading.set(true);

    const request =
      mode === 'smart'
        ? this.incidentService.smartSearch(description)
        : this.incidentService.search(description);

    request
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response: IncidentSearchResponse) => {
          this.responseMessage.set(response.message);
          this.results.set(response.results.slice(0, 3));
          this.aiAnalysis.set(response.aiAnalysis);
          this.hasSearched.set(true);
        },
        error: (error: Error) => {
          this.errorMessage.set(error.message);
        },
      });
  }

  clear(): void {
    this.description.set('');
    this.validationMessage.set('');
    this.errorMessage.set('');
    this.responseMessage.set('');
    this.results.set([]);
    this.aiAnalysis.set(undefined);
    this.hasSearched.set(false);
  }

  similarityWidth(percentage: number): number {
    return Math.max(0, Math.min(percentage, 100));
  }
}
